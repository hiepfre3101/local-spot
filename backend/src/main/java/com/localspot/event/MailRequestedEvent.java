package com.localspot.event;

import com.localspot.amqp.EmailMessage;

/** Service muốn gửi mail; chỉ được đẩy lên queue sau khi transaction commit (xem MailQueuePublisher). */
public record MailRequestedEvent(EmailMessage message) {}
