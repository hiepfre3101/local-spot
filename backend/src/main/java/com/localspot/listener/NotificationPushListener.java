package com.localspot.listener;

import com.localspot.event.NotificationCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Đẩy thông báo vừa commit tới mọi phiên STOMP đang mở của người nhận ({@code /user/queue/notifications}). Người nhận
 * không trực tuyến thì bỏ qua — thông báo đã nằm trong CSDL, lần mở ứng dụng sau đọc qua {@code GET /me/notifications}.
 * Đẩy lỗi không làm hỏng thao tác đã commit.
 */
@Component
public class NotificationPushListener {

    static final String DESTINATION = "/queue/notifications";

    private static final Logger log = LoggerFactory.getLogger(NotificationPushListener.class);

    private final SimpMessagingTemplate messaging;

    public NotificationPushListener(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void push(NotificationCreatedEvent event) {
        try {
            messaging.convertAndSendToUser(String.valueOf(event.recipientId()), DESTINATION, event.notification());
        } catch (MessagingException e) {
            log.warn(
                    "Không đẩy được thông báo {} realtime: {}",
                    event.notification().id(),
                    e.getMessage());
        }
    }
}
