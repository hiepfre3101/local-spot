package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.config.RabbitConfig;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Luồng mail đầu-cuối: API → event sau commit → RabbitMQ → MailConsumer → SMTP (thay bằng mock) → lấy token từ nội
 * dung mail → gọi API xác thực / đặt lại mật khẩu. Consumer chỉ bật ở lớp test này.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MailFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String NEW_PASSWORD = "MatKhauMoi2026";
    private static final String COOKIE = "refresh_token";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @MockitoBean
    private JavaMailSender mailSender;

    private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
    private final List<String> failedAttempts = new CopyOnWriteArrayList<>();

    @BeforeEach
    void captureOutgoingMail() {
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(i -> {
                    MimeMessage message = i.getArgument(0);
                    String to = recipientOf(message);
                    if (to.startsWith("smtp-down-")) {
                        failedAttempts.add(to);
                        throw new MailSendException("SMTP không phản hồi");
                    }
                    sent.add(message);
                    return null;
                })
                .when(mailSender)
                .send(any(MimeMessage.class));
    }

    @Test
    void registrationMailLinkVerifiesEmailOnce() throws Exception {
        String email = uniqueEmail("verify");
        register(email).andExpect(status().isCreated());
        String token = tokenFromMailTo(email, 1);
        String accessToken = accessToken(email, PASSWORD);

        verifyEmail(token).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(jsonPath("$.emailVerified").value(true))
                // requirements §5.1: tài khoản mới vừa xác thực → base 10
                .andExpect(jsonPath("$.trustScore").value(10));
        verifyEmail(token)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void verificationMailIsWellFormed() throws Exception {
        String email = uniqueEmail("format");
        register(email).andExpect(status().isCreated());
        MimeMessage message = awaitMailTo(email, 1).getFirst();

        assertThat(message.getSubject()).isEqualTo("Xác thực email tài khoản LocalSpot");
        assertThat(message.getFrom()[0].toString()).contains("no-reply@localspot.test");
        assertThat(textOf(message)).contains("Chào Người thử,", "http://localhost:5173/verify-email?token=", "24 giờ");
    }

    @Test
    void resendInvalidatesPreviousLinkAndStopsOnceVerified() throws Exception {
        String email = uniqueEmail("resend");
        register(email).andExpect(status().isCreated());
        String first = tokenFromMailTo(email, 1);
        String accessToken = accessToken(email, PASSWORD);

        mvc.perform(post("/api/v1/auth/resend-verification").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        String second = tokenFromMailTo(email, 2);

        assertThat(second).isNotEqualTo(first);
        verifyEmail(first).andExpect(status().isGone());
        verifyEmail(second).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/resend-verification").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        assertThat(tokenCount(email, "EMAIL_VERIFY")).isEqualTo(2);
    }

    @Test
    void resendRequiresLogin() throws Exception {
        mvc.perform(post("/api/v1/auth/resend-verification"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void expiredLinkIsGone() throws Exception {
        String email = uniqueEmail("expired");
        register(email).andExpect(status().isCreated());
        String token = tokenFromMailTo(email, 1);
        jdbc.update(
                "UPDATE user_tokens t JOIN users u ON u.id = t.user_id SET t.expires_at = ? WHERE u.email = ?",
                Timestamp.from(Instant.now().minusSeconds(60)),
                email);

        verifyEmail(token).andExpect(status().isGone());
    }

    @Test
    void passwordResetChangesPasswordAndEndsEverySession() throws Exception {
        String email = uniqueEmail("reset");
        register(email).andExpect(status().isCreated());
        String oldSession = refreshCookieOf(login(email, PASSWORD).andReturn());

        forgotPassword(email.toUpperCase()).andExpect(status().isNoContent());
        String token = tokenFromMailTo(email, 2); // mail 1 là mail xác thực

        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());

        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
        refresh(oldSession).andExpect(status().isUnauthorized());
        resetPassword(token, "LanThuHai2026").andExpect(status().isGone());
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownEmails() throws Exception {
        forgotPassword(uniqueEmail("nobody")).andExpect(status().isNoContent());
    }

    @Test
    void resetRejectsWeakPassword() throws Exception {
        resetPassword("bat-ky", "yeu").andExpect(status().isUnprocessableContent());
    }

    @Test
    void changePasswordKeepsThisDeviceAndLogsOutOthers() throws Exception {
        String email = uniqueEmail("change");
        register(email).andExpect(status().isCreated());
        MvcResult thisDevice = login(email, PASSWORD).andReturn();
        String otherDevice = refreshCookieOf(login(email, PASSWORD).andReturn());
        String accessToken = JsonPath.read(thisDevice.getResponse().getContentAsString(), "$.accessToken");

        changePassword(accessToken, "SaiMatKhau1", NEW_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        MvcResult changed = changePassword(accessToken, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        refresh(otherDevice).andExpect(status().isUnauthorized());
        refresh(refreshCookieOf(thisDevice)).andExpect(status().isUnauthorized());
        refresh(refreshCookieOf(changed)).andExpect(status().isOk());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void failingSmtpIsRetriedThenDeadLettered() throws Exception {
        String email = uniqueEmail("smtp-down");
        long before = deadLetterCount();

        // Request chính vẫn thành công dù gửi mail lỗi (UC01 ngoại lệ 5a)
        register(email).andExpect(status().isCreated());

        long deadline = System.currentTimeMillis() + 15_000;
        while (deadLetterCount() <= before && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(deadLetterCount()).isEqualTo(before + 1);
        // max-attempts = 4 (application.yml)
        assertThat(failedAttempts.stream().filter(email::equals)).hasSize(4);
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","displayName":"Người thử"}
                        """.formatted(email, PASSWORD)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private String accessToken(String email, String password) throws Exception {
        return JsonPath.read(
                login(email, password)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.accessToken");
    }

    private ResultActions verifyEmail(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token)));
    }

    private ResultActions forgotPassword(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return mvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)));
    }

    private ResultActions changePassword(String accessToken, String current, String next) throws Exception {
        return mvc.perform(put("/api/v1/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, token)));
    }

    /** Token trong mail thứ {@code nth} (đếm từ 1) gửi tới địa chỉ này. */
    private String tokenFromMailTo(String email, int nth) throws Exception {
        String text = textOf(awaitMailTo(email, nth).get(nth - 1));
        Matcher matcher = TOKEN.matcher(text);
        assertThat(matcher.find()).as("mail chứa link có token").isTrue();
        return matcher.group(1);
    }

    private List<MimeMessage> awaitMailTo(String email, int count) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            List<MimeMessage> toEmail =
                    sent.stream().filter(m -> email.equals(recipientOf(m))).toList();
            if (toEmail.size() >= count) {
                return toEmail;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Không nhận được " + count + " mail gửi tới " + email);
    }

    private long deadLetterCount() {
        QueueInformation info = amqpAdmin.getQueueInfo(RabbitConfig.MAIL_DLQ);
        return info == null ? 0 : info.getMessageCount();
    }

    private Integer tokenCount(String email, String type) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tokens t JOIN users u ON u.id = t.user_id WHERE u.email = ? AND t.type = ?",
                Integer.class,
                email,
                type);
    }

    private static String recipientOf(MimeMessage message) {
        try {
            return message.getRecipients(Message.RecipientType.TO)[0].toString();
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String textOf(Part part) throws Exception {
        if (part instanceof MimeMessage message) {
            message.saveChanges(); // cập nhật header Content-Type từ nội dung multipart như khi gửi thật
        }
        if (part.isMimeType("text/plain")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String text = textOf(multipart.getBodyPart(i));
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String refreshCookieOf(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull().startsWith(COOKIE + "=");
        return setCookie.substring(COOKIE.length() + 1, setCookie.indexOf(';'));
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
