package com.localspot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localspot.TestcontainersConfiguration;
import com.localspot.config.RateLimitProperties;
import com.localspot.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Thuật toán cửa sổ trượt trên Redis thật (Testcontainers) với đồng hồ điều khiển được — kiểm tra trượt cửa sổ mà không
 * cần {@code sleep}. Ngưỡng thử: 3 lần / 10 phút.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RateLimiterTests {

    private static final int LIMIT = 3;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    @Autowired
    private StringRedisTemplate redis;

    private MutableClock clock;
    private RateLimiter limiter;
    private String subject;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-04T08:00:00Z"));
        limiter = new RateLimiter(redis, properties(), clock);
        subject = "nguoi-thu-" + UUID.randomUUID() + "@localspot.test";
    }

    @Test
    void allowsUpToLimitThenRejectsWithRetryAfter() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.1");
            clock.advance(Duration.ofMinutes(1));
        }
        // Lần cũ nhất lúc 08:00 → hết hạn 08:10; hiện là 08:03 → còn 7 phút
        assertThatThrownBy(() -> limiter.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.1"))
                .isInstanceOfSatisfying(RateLimitExceededException.class, e -> {
                    assertThat(e.getRetryAfterSeconds())
                            .isEqualTo(Duration.ofMinutes(7).toSeconds());
                    assertThat(e.getCode()).isEqualTo("TOO_MANY_REQUESTS");
                    assertThat(e.getMessage()).contains("7 phút");
                });
    }

    @Test
    void windowSlidesOneSlotAtATime() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.1");
            clock.advance(Duration.ofMinutes(1));
        }
        // 08:10 — chỉ lần lúc 08:00 rời cửa sổ → đúng một lượt mới, không phải reset cả loạt như cửa sổ cố định
        clock.set(Instant.parse("2026-10-04T08:10:00Z"));
        limiter.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.1");
        assertThatThrownBy(() -> limiter.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void rejectedAttemptsAreNotCounted() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.1");
        }
        // Thử dồn khi đang bị chặn không kéo dài thời gian chặn
        for (int i = 0; i < 20; i++) {
            clock.advance(Duration.ofSeconds(10));
            assertThatThrownBy(() -> limiter.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.1"))
                    .isInstanceOf(RateLimitExceededException.class);
        }
        clock.set(Instant.parse("2026-10-04T08:10:00Z"));
        for (int i = 0; i < LIMIT; i++) {
            limiter.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.1");
        }
    }

    @Test
    void keysAreIndependentPerSubjectAndPolicy() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.1");
        }
        assertThatCode(() -> limiter.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.2"))
                .doesNotThrowAnyException();
        assertThatCode(() -> limiter.acquire(RateLimitPolicy.FORGOT_PASSWORD_EMAIL, subject))
                .doesNotThrowAnyException();
    }

    @Test
    void resetClearsTheCounter() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.1");
        }
        limiter.reset(RateLimitPolicy.LOGIN, subject, "10.0.0.1");
        for (int i = 0; i < LIMIT; i++) {
            limiter.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.1");
        }
    }

    @Test
    void storesHashedKeysThatExpire() {
        limiter.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.1");

        Set<String> keys = redis.keys("rl:login:*");
        assertThat(keys).isNotEmpty().noneMatch(k -> k.contains(subject) || k.contains("10.0.0.1"));
        // Khóa tự hết hạn sau một cửa sổ — không tích rác
        assertThat(keys).allSatisfy(k -> assertThat(redis.getExpire(k)).isPositive());
    }

    @Test
    void failsOpenWhenRedisIsDown(CapturedOutput output) {
        LettuceConnectionFactory deadRedis = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", 1),
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofMillis(200))
                        .build());
        deadRedis.afterPropertiesSet();
        deadRedis.start();
        try {
            RateLimiter offline = new RateLimiter(new StringRedisTemplate(deadRedis), properties(), clock);
            for (int i = 0; i < LIMIT + 2; i++) {
                offline.acquire(RateLimitPolicy.LOGIN, subject, "10.0.0.1");
            }
            assertThatCode(() -> offline.reset(RateLimitPolicy.LOGIN, subject, "10.0.0.1"))
                    .doesNotThrowAnyException();
            assertThat(output.getAll()).contains("fail-open");
        } finally {
            deadRedis.destroy();
        }
    }

    private static RateLimitProperties properties() {
        Map<RateLimitPolicy, RateLimitProperties.Rule> rules = new EnumMap<>(RateLimitPolicy.class);
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            rules.put(policy, new RateLimitProperties.Rule(LIMIT, WINDOW));
        }
        return new RateLimitProperties(rules);
    }

    /** Đồng hồ chỉnh tay được — RateLimiter nhận thời điểm từ ứng dụng nên kiểm soát được trượt cửa sổ. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
