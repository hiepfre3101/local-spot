package com.localspot.listener;

import com.localspot.config.RabbitConfig;
import com.localspot.event.MailRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Đẩy mail lên RabbitMQ <b>sau khi</b> transaction commit: không gửi link xác thực cho một tài khoản mà transaction
 * tạo nó bị rollback, và token trong link chắc chắn đã có trong CSDL khi người dùng bấm.
 *
 * <p>Đánh đổi: nếu RabbitMQ sập đúng lúc giữa commit và publish thì mail bị mất (không có transactional outbox). Người
 * dùng tự khắc phục bằng "gửi lại email xác thực" / "quên mật khẩu" lần nữa; lỗi không lan ra request đã thành công.
 */
@Component
public class MailQueuePublisher {

    private static final Logger log = LoggerFactory.getLogger(MailQueuePublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public MailQueuePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(MailRequestedEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.TASKS_EXCHANGE, RabbitConfig.MAIL_ROUTING_KEY, event.message());
        } catch (AmqpException e) {
            log.error(
                    "Không đẩy được mail '{}' lên RabbitMQ: {}", event.message().subject(), e.getMessage());
        }
    }
}
