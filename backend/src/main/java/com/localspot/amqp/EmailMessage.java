package com.localspot.amqp;

/**
 * Message trên queue {@code mail.send}: nội dung đã dựng xong ở phía gửi, consumer chỉ chuyển cho SMTP — consumer không
 * cần truy cập CSDL và dùng chung cho mọi loại mail.
 */
public record EmailMessage(String to, String subject, String textBody, String htmlBody) {}
