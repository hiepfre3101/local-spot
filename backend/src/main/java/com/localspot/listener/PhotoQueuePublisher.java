package com.localspot.listener;

import com.localspot.amqp.PhotoProcessMessage;
import com.localspot.config.RabbitConfig;
import com.localspot.event.PhotoObjectsDeletedEvent;
import com.localspot.event.PlacePhotosStoredEvent;
import com.localspot.storage.ObjectStorage;
import com.localspot.storage.StorageException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Nối transaction ảnh với hàng đợi / kho object — như {@link MailQueuePublisher}: chỉ đẩy việc xử lý khi bản ghi đã
 * commit (consumer chắc chắn đọc thấy), và chỉ xóa object khi bản ghi đã thật sự bị xóa.
 *
 * <p>Đánh đổi (không có transactional outbox): RabbitMQ sập đúng lúc sau commit → ảnh kẹt PROCESSING; kho lỗi khi dọn →
 * còn object rác. Cả hai chỉ được ghi log, không làm hỏng request đã thành công.
 */
@Component
public class PhotoQueuePublisher {

    private static final Logger log = LoggerFactory.getLogger(PhotoQueuePublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectStorage storage;

    public PhotoQueuePublisher(RabbitTemplate rabbitTemplate, ObjectStorage storage) {
        this.rabbitTemplate = rabbitTemplate;
        this.storage = storage;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enqueue(PlacePhotosStoredEvent event) {
        for (Long photoId : event.photoIds()) {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitConfig.TASKS_EXCHANGE, RabbitConfig.PHOTO_ROUTING_KEY, new PhotoProcessMessage(photoId));
            } catch (AmqpException e) {
                log.error("Không đẩy được ảnh {} lên RabbitMQ: {}", photoId, e.getMessage());
            }
        }
    }

    /** Request thất bại sau khi ảnh gốc đã lên kho (lỗi validate sau đó, trùng slug…) → không để ảnh mồ côi. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void discardIncoming(PlacePhotosStoredEvent event) {
        deleteQuietly(event.incomingKeys());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleteObjects(PhotoObjectsDeletedEvent event) {
        deleteQuietly(event.keys());
    }

    private void deleteQuietly(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        try {
            storage.delete(keys);
        } catch (StorageException e) {
            log.warn("Không xóa được {} object ảnh: {}", keys.size(), e.getMessage());
        }
    }
}
