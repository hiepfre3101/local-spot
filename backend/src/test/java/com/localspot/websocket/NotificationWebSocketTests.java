package com.localspot.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Đẩy thông báo realtime (FR-27) qua máy chủ thật (cổng ngẫu nhiên) và client STOMP thật: CONNECT phải có access token
 * hợp lệ, thông báo vừa commit tới đúng phiên của người nhận và không tới người khác, chỉ được đăng ký kênh cá nhân.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationWebSocketTests {

    private static final String PASSWORD = "LocalSpot2026";

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private WebSocketStompClient client;

    @BeforeEach
    void setUp() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
    }

    @AfterEach
    void tearDown() {
        client.stop();
    }

    @Test
    void connectRequiresValidAccessToken() throws Exception {
        // Máy chủ trả ERROR cho CONNECT rồi đóng phiên → future lỗi ngay (không phải hết giờ chờ)
        for (String token : new String[] {null, "not-a-jwt"}) {
            assertThatThrownBy(() -> connect(token)).isInstanceOf(ExecutionException.class);
        }
        connect(account().accessToken()).disconnect(); // đối chứng: token hợp lệ kết nối được
    }

    @Test
    void committedNotificationIsPushedOnlyToRecipient() throws Exception {
        Account proposer = account();
        Account bystander = account();
        BlockingQueue<String> proposerInbox = subscribe(connect(proposer.accessToken()));
        BlockingQueue<String> bystanderInbox = subscribe(connect(bystander.accessToken()));
        long placeId = pendingPlace(proposer);

        mvc.perform(post("/api/v1/moderation/places/" + placeId + "/decision")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + account("MODERATOR").accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"APPROVE\"}"))
                .andExpect(status().isNoContent());

        String pushed = proposerInbox.poll(5, TimeUnit.SECONDS);
        assertThat(pushed).isNotNull();
        assertThat(JsonPath.<String>read(pushed, "$.type")).isEqualTo("PLACE_APPROVED");
        assertThat(JsonPath.<Integer>read(pushed, "$.data.placeId").longValue()).isEqualTo(placeId);
        assertThat(JsonPath.<Integer>read(pushed, "$.id").longValue())
                .isEqualTo(jdbc.queryForObject(
                        "SELECT id FROM notifications WHERE user_id = ?", Long.class, proposer.id()));
        assertThat(bystanderInbox.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void onlyThePersonalQueueCanBeSubscribed() throws Exception {
        StompSession session = connect(account().accessToken());
        session.subscribe("/queue/notifications-user42", new Collector(new LinkedBlockingQueue<>()));
        long deadline = System.currentTimeMillis() + 5_000;
        while (session.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(session.isConnected()).isFalse(); // máy chủ trả ERROR và đóng phiên
    }

    // ─── Tiện ích ────────────────────────────────────────────────────────────

    private StompSession connect(String accessToken) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (accessToken != null) {
            connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return client.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private static BlockingQueue<String> subscribe(StompSession session) throws InterruptedException {
        BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        session.subscribe(StompAuthInterceptor.NOTIFICATION_DESTINATION, new Collector(inbox));
        Thread.sleep(200); // SUBSCRIBE là bất đồng bộ — chờ broker ghi nhận trước khi phát
        return inbox;
    }

    private record Collector(BlockingQueue<String> inbox) implements StompFrameHandler {

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            inbox.add(new String((byte[]) payload, StandardCharsets.UTF_8));
        }
    }

    private record Account(long id, String accessToken) {}

    private Account account(String... roles) throws Exception {
        String email = "ws-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử realtime"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        jdbc.update("UPDATE users SET email_verified_at = UTC_TIMESTAMP(6) WHERE id = ?", id);
        for (String role : roles) {
            jdbc.update(
                    "INSERT IGNORE INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = ?",
                    id,
                    role);
        }
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new Account(id, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
    }

    private long pendingPlace(Account proposer) {
        Long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'pho-bun'", Long.class);
        String slug = "ws-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES (?, ?, 'Quán thử realtime', ?, '1 Phố Thử', 'Hà Nội',
                        ST_PointFromText('POINT(21.03 105.85)', 4326), 'PENDING', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, category, proposer.id(), slug);
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }
}
