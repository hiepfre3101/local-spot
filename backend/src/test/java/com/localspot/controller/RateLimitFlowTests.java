package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Giới hạn tần suất qua HTTP (NFR-10: "test vượt ngưỡng trả 429") với đúng ngưỡng production — profile test nới ngưỡng
 * đăng ký cho các lớp test khác nên lớp này đặt lại {@code register = 10}. Mỗi test dùng IP / email riêng để không
 * giẫm bộ đếm của nhau.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "localspot.rate-limit.policies.register.limit=10")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitFlowTests {

    private static final String PASSWORD = "LocalSpot2026";

    @Autowired
    private MockMvc mvc;

    // ─── Đăng nhập (U2: 5 lần sai / 15 phút theo email + IP) ────────────────

    @Test
    void sixthLoginAttemptWithin15MinutesIs429EvenWithCorrectPassword() throws Exception {
        String ip = randomIp();
        String email = registered(ip);
        for (int i = 0; i < 5; i++) {
            login(email, "SaiMatKhau1", ip).andExpect(status().isUnauthorized());
        }

        login(email, "SaiMatKhau1", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        // Không cho biết mật khẩu đúng khi đang bị chặn
        String retryAfter = login(email, PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 15 * 60L);
    }

    @Test
    void loginLimitIsPerEmailAndIp() throws Exception {
        String ip = randomIp();
        String email = registered(ip);
        for (int i = 0; i < 5; i++) {
            login(email, "SaiMatKhau1", ip).andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD, ip).andExpect(status().isTooManyRequests());

        // Người dùng thật ở mạng khác không bị khóa theo kẻ dò mật khẩu
        login(email, PASSWORD, randomIp()).andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsFailedAttempts() throws Exception {
        String ip = randomIp();
        String email = registered(ip);
        for (int i = 0; i < 4; i++) {
            login(email, "SaiMatKhau1", ip).andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD, ip).andExpect(status().isOk());

        // Bộ đếm về 0 → lại được 5 lần sai
        for (int i = 0; i < 5; i++) {
            login(email, "SaiMatKhau1", ip).andExpect(status().isUnauthorized());
        }
        login(email, "SaiMatKhau1", ip).andExpect(status().isTooManyRequests());
    }

    // ─── Đăng ký (10 / giờ / IP) ─────────────────────────────────────────────

    @Test
    void eleventhRegistrationFromOneIpIs429() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 10; i++) {
            register(uniqueEmail(), ip).andExpect(status().isCreated());
        }
        register(uniqueEmail(), ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        register(uniqueEmail(), randomIp()).andExpect(status().isCreated());
    }

    // ─── Quên mật khẩu (3 / giờ / email, 20 / giờ / IP) ─────────────────────

    @Test
    void fourthResetRequestForOneEmailIs429EvenIfEmailUnknown() throws Exception {
        String known = registered(randomIp());
        String unknown = uniqueEmail();
        for (String email : new String[] {known, unknown}) {
            for (int i = 0; i < 3; i++) {
                forgotPassword(email, randomIp()).andExpect(status().isNoContent());
            }
            // Cùng phản hồi cho email có / không tồn tại — 429 không tiết lộ email nào đã đăng ký
            forgotPassword(email, randomIp())
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        }
    }

    @Test
    void twentyFirstResetRequestFromOneIpIs429() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 20; i++) {
            forgotPassword(uniqueEmail(), ip).andExpect(status().isNoContent());
        }
        forgotPassword(uniqueEmail(), ip).andExpect(status().isTooManyRequests());
    }

    // ─── Gửi lại mail xác thực (3 / giờ / tài khoản) ─────────────────────────

    @Test
    void fourthResendWithinAnHourIs429() throws Exception {
        String ip = randomIp();
        String email = registered(ip);
        String accessToken = JsonPath.read(
                login(email, PASSWORD, ip).andReturn().getResponse().getContentAsString(), "$.accessToken");

        for (int i = 0; i < 3; i++) {
            resend(accessToken).andExpect(status().isNoContent());
        }
        resend(accessToken).andExpect(status().isTooManyRequests());
    }

    // ─── Hỗ trợ ──────────────────────────────────────────────────────────────

    private String registered(String ip) throws Exception {
        String email = uniqueEmail();
        register(email, ip).andExpect(status().isCreated());
        return email;
    }

    private ResultActions register(String email, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                .with(from(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","displayName":"Người thử"}
                        """.formatted(email, PASSWORD)));
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .with(from(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private ResultActions forgotPassword(String email, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/forgot-password")
                .with(from(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}
                        """.formatted(email)));
    }

    private ResultActions resend(String accessToken) throws Exception {
        return mvc.perform(
                post("/api/v1/auth/resend-verification").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    /** IP client như sau nginx ({@code forward-headers-strategy} đã đặt {@code remoteAddr} = IP thật). */
    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + (1 + r.nextInt(254));
    }

    private static String uniqueEmail() {
        return "rl-" + UUID.randomUUID() + "@localspot.test";
    }
}
