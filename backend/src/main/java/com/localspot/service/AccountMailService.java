package com.localspot.service;

import com.localspot.amqp.EmailMessage;
import com.localspot.config.AppProperties;
import com.localspot.entity.User;
import com.localspot.event.MailRequestedEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Dựng nội dung mail tài khoản (văn bản + HTML đơn giản, không thêm template engine ngoài stack) và phát event để gửi
 * sau commit. Link trỏ về route frontend trong sitemap; frontend gọi API tương ứng.
 */
@Service
public class AccountMailService {

    private final AppProperties app;
    private final ApplicationEventPublisher events;

    public AccountMailService(AppProperties app, ApplicationEventPublisher events) {
        this.app = app;
        this.events = events;
    }

    public void sendEmailVerification(User user, String rawToken) {
        String link = app.link("/verify-email?token=" + encode(rawToken));
        send(
                user,
                "Xác thực email tài khoản LocalSpot",
                "Bấm vào liên kết dưới đây để xác thực email. Liên kết có hiệu lực trong 24 giờ.",
                "Xác thực email",
                link,
                "Nếu bạn không đăng ký LocalSpot, hãy bỏ qua email này.");
    }

    public void sendPasswordReset(User user, String rawToken) {
        String link = app.link("/reset-password?token=" + encode(rawToken));
        send(
                user,
                "Đặt lại mật khẩu LocalSpot",
                "Chúng tôi nhận được yêu cầu đặt lại mật khẩu. Liên kết có hiệu lực trong 30 phút và chỉ dùng được một lần.",
                "Đặt mật khẩu mới",
                link,
                "Nếu bạn không yêu cầu, hãy bỏ qua email này — mật khẩu hiện tại vẫn giữ nguyên.");
    }

    private void send(User user, String subject, String intro, String action, String link, String footer) {
        String greeting = "Chào " + user.getDisplayName() + ",";
        String text = greeting + "\n\n" + intro + "\n\n" + link + "\n\n" + footer + "\n\n— LocalSpot";
        String html = "<p>" + HtmlUtils.htmlEscape(greeting) + "</p>"
                + "<p>" + HtmlUtils.htmlEscape(intro) + "</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(link) + "\">" + HtmlUtils.htmlEscape(action) + "</a></p>"
                + "<p>" + HtmlUtils.htmlEscape(footer) + "</p>"
                + "<p>— LocalSpot</p>";
        events.publishEvent(new MailRequestedEvent(new EmailMessage(user.getEmail(), subject, text, html)));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
