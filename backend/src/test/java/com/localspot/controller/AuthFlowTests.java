package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Luồng xác thực đầu-cuối qua HTTP trên MySQL thật: đăng ký → đăng nhập → gọi API bằng access token → refresh xoay vòng
 * → phát hiện dùng lại → đăng xuất. Không dùng @Transactional ở lớp test để các transaction của service commit thật
 * (cần thiết với kiểm tra "thu hồi phải được commit dù trả 401"); mỗi test dùng email riêng nên không giẫm dữ liệu.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String COOKIE = "refresh_token";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void registersThenLogsInWithRefreshCookieAndProfile() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        MvcResult login = login(email.toUpperCase(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.roles[0]").value("USER"))
                // Quyền gộp từ role cho frontend — thành viên chỉ có 4 quyền của USER (V2)
                .andExpect(jsonPath("$.user.permissions")
                        .value(org.hamcrest.Matchers.contains(
                                "comment:delete-own", "review:create", "review:delete-own", "review:update-own")))
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                // Chưa xác thực email, tài khoản 0 ngày: không có base → trust 0
                .andExpect(jsonPath("$.user.trustScore").value(0))
                .andReturn();

        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(COOKIE + "=")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=604800");
        assertThat(JsonPath.<String>read(login.getResponse().getContentAsString(), "$.accessToken"))
                .isNotBlank();
        assertThat(login.getResponse().getContentAsString()).doesNotContain(refreshTokenOf(login));
    }

    @Test
    void rejectsDuplicateEmailCaseInsensitively() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        register(email.toUpperCase(), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void validatesRegistrationAs422WithFieldErrors() throws Exception {
        register("khong-phai-email", "ngan")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
    }

    @Test
    void ignoresMassAssignedFields() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Kẻ gian",
                                 "roles":["ADMIN"],"emailVerifiedAt":"2020-01-01T00:00:00Z","helpfulVotesCount":999}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());

        login(email, PASSWORD)
                .andExpect(jsonPath("$.user.roles.length()").value(1))
                .andExpect(jsonPath("$.user.roles[0]").value("USER"))
                .andExpect(jsonPath("$.user.emailVerified").value(false));
    }

    @Test
    void wrongPasswordAndUnknownEmailLookIdentical() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        String wrongPassword = login(email, "SaiMatKhau1")
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String unknownEmail = login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(JsonPath.<String>read(wrongPassword, "$.code")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(JsonPath.<String>read(unknownEmail, "$.detail"))
                .isEqualTo(JsonPath.<String>read(wrongPassword, "$.detail"));
    }

    @Test
    void lockedAccountCannotLogIn() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        lock(email);

        login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.detail", containsString("Spam")));
    }

    @Test
    void accessTokenAuthenticatesApiCalls() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        String accessToken = accessTokenOf(login(email, PASSWORD).andReturn());

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredForgedAndWrongIssuerTokens() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        long userId = userIdOf(email);
        Instant now = Instant.now();

        String expired = sign(jwtEncoder, "localspot", userId, now.minusSeconds(3600), now.minusSeconds(120));
        String wrongIssuer = sign(jwtEncoder, "attacker", userId, now, now.plusSeconds(900));
        JwtEncoder otherKey =
                new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(new byte[32], "HmacSHA256")));
        String forged = sign(otherKey, "localspot", userId, now, now.plusSeconds(900));

        for (String token : new String[] {expired, wrongIssuer, forged}) {
            mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void lockingAnAccountRevokesItsAccessImmediately() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        String accessToken = accessTokenOf(login(email, PASSWORD).andReturn());

        lock(email);

        // Token còn hạn 15 phút nhưng quyền đọc từ CSDL mỗi request → bị chặn ngay
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void refreshRotatesTokenAndOldTokenStopsWorking() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        String first = refreshTokenOf(login(email, PASSWORD).andReturn());

        MvcResult refreshed = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String second = refreshTokenOf(refreshed);

        assertThat(second).isNotEqualTo(first);
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void reusingRotatedTokenRevokesEverySession() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        String stolen = refreshTokenOf(login(email, PASSWORD).andReturn());
        String otherDevice = refreshTokenOf(login(email, PASSWORD).andReturn());
        String legit = refreshTokenOf(refresh(stolen).andExpect(status().isOk()).andReturn());

        // Kẻ gian dùng lại token đã xoay vòng
        refresh(stolen)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));

        // Thu hồi đã được commit dù request trả 401: mọi phiên khác cũng mất hiệu lực (U2)
        refresh(legit).andExpect(status().isUnauthorized());
        refresh(otherDevice).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("""
                        SELECT COUNT(*) FROM refresh_tokens t JOIN users u ON u.id = t.user_id
                        WHERE u.email = ? AND t.used_at IS NULL AND t.revoked_at IS NULL
                        """, Integer.class, email)).isZero();
    }

    @Test
    void refreshWithoutOrWithUnknownCookieIs401() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        refresh("khong-ton-tai").andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTokenAndClearsCookie() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        String token = refreshTokenOf(login(email, PASSWORD).andReturn());

        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, token)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        refresh(token).andExpect(status().isUnauthorized());
        // Đăng xuất lần nữa / không có cookie vẫn 204
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions register(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","displayName":"Người thử"}
                        """.formatted(email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.USER_AGENT, "AuthFlowTests")
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, token)));
    }

    private void lock(String email) {
        jdbc.update(
                "UPDATE users SET locked_until = ?, lock_reason = 'Spam' WHERE email = ?",
                Timestamp.from(Instant.now().plusSeconds(86_400)),
                email);
    }

    private long userIdOf(String email) {
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        return id;
    }

    private static String sign(JwtEncoder encoder, String issuer, long userId, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(Long.toString(userId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String accessTokenOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String refreshTokenOf(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull().startsWith(COOKIE + "=");
        return setCookie.substring(COOKIE.length() + 1, setCookie.indexOf(';'));
    }

    private static String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@example.com";
    }
}
