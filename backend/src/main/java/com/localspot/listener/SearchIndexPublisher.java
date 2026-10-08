package com.localspot.listener;

import com.localspot.amqp.SearchReindexMessage;
import com.localspot.config.RabbitConfig;
import com.localspot.event.PlaceIndexChangedEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Đẩy yêu cầu đồng bộ index tìm kiếm lên queue {@code search.reindex} — sau commit (consumer chắc chắn đọc thấy dữ liệu
 * mới), chia lô ≤ 100 id mỗi message (đổi tên danh mục có thể chạm hàng trăm địa điểm).
 *
 * <p>Đánh đổi như {@link PhotoQueuePublisher} (không có outbox): RabbitMQ sập đúng lúc sau commit → index lệch tới lần
 * đồng bộ toàn bộ kế tiếp lúc khởi động ({@code localspot.search.reindex-on-startup}); chỉ ghi log. Kết quả lệch không
 * lộ địa điểm chưa duyệt: kết quả tìm luôn được nạp lại và lọc APPROVED trong MySQL.
 */
@Component
public class SearchIndexPublisher {

    static final int BATCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(SearchIndexPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public SearchIndexPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlacesChanged(PlaceIndexChangedEvent event) {
        enqueue(event.placeIds());
    }

    /** Gửi ngay, không chờ transaction — cho đồng bộ lúc khởi động và tự sửa kết quả cũ khi tìm. */
    public void enqueue(List<Long> placeIds) {
        for (int from = 0; from < placeIds.size(); from += BATCH_SIZE) {
            List<Long> batch = List.copyOf(placeIds.subList(from, Math.min(from + BATCH_SIZE, placeIds.size())));
            try {
                rabbitTemplate.convertAndSend(
                        RabbitConfig.TASKS_EXCHANGE, RabbitConfig.SEARCH_ROUTING_KEY, new SearchReindexMessage(batch));
            } catch (AmqpException e) {
                log.error("Không đẩy được yêu cầu đồng bộ tìm kiếm cho {} địa điểm: {}", batch.size(), e.getMessage());
            }
        }
    }
}
