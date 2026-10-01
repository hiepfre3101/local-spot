package com.localspot.amqp;

import com.localspot.config.MailSenderProperties;
import com.localspot.config.RabbitConfig;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Gửi mail từ queue. Ném lỗi (SMTP sập, timeout) → listener retry theo cấu hình, hết lượt thì message sang DLQ (NFR-13).
 * Request đăng ký / quên mật khẩu đã trả về từ trước nên không bị ảnh hưởng (UC01 ngoại lệ 5a).
 */
@Component
public class MailConsumer {

    private static final Logger log = LoggerFactory.getLogger(MailConsumer.class);

    private final JavaMailSender mailSender;
    private final MailSenderProperties properties;

    public MailConsumer(JavaMailSender mailSender, MailSenderProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @RabbitListener(queues = RabbitConfig.MAIL_QUEUE)
    public void send(EmailMessage message) throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.from());
        helper.setTo(message.to());
        helper.setSubject(message.subject());
        helper.setText(message.textBody(), message.htmlBody());
        try {
            mailSender.send(mime);
        } catch (MailException e) {
            // Không ghi địa chỉ người nhận ra log (NFR-11) — chỉ tiêu đề để truy vết
            log.warn("Gửi mail '{}' thất bại, sẽ thử lại: {}", message.subject(), e.getMessage());
            throw e;
        }
    }
}
