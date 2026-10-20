package com.localspot.security;

import com.localspot.config.RateLimitProperties;
import com.localspot.exception.RateLimitExceededException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Giới hạn tần suất bằng Redis (NFR-10, chốt 2026-10-04: Redis, không thêm thư viện). Thuật toán cửa sổ trượt
 * ({@code redis/sliding-window-rate-limit.lua}) chạy nguyên tử: "tối đa N lần trong mọi khoảng T liên tiếp". Bộ đếm
 * cửa sổ cố định ({@code INCR} + {@code EXPIRE}) đơn giản hơn nhưng cho dồn gần 2N lần quanh ranh giới hai cửa sổ.
 *
 * <ul>
 *   <li><b>Khóa băm SHA-256</b>: email / IP không nằm dạng rõ trong Redis (NFR-11); khóa tự hết hạn sau một cửa sổ.
 *   <li><b>Redis sập → cho qua</b> + log ERROR (chốt 2026-10-04): Redis là phụ trợ, không phải nguồn dữ liệu chính;
 *       chặn hết sẽ biến Redis thành điểm hỏng duy nhất (không ai đăng nhập được). Brute force vẫn bị BCrypt làm chậm;
 *       health check báo Redis DOWN. Timeout lệnh Redis giới hạn 500 ms (application.yml) để "cho qua" không thành treo.
 * </ul>
 */
@Component
public final class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final String KEY_PREFIX = "rl:";
    private static final String SCRIPT = "redis/sliding-window-rate-limit.lua";

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final Clock clock;
    private final RedisScript<List> script;

    public RateLimiter(StringRedisTemplate redis, RateLimitProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
        this.script = new DefaultRedisScript<>(loadScript(), List.class);
    }

    /**
     * Ghi nhận một lần thử; vượt ngưỡng → {@link RateLimitExceededException} (429 + {@code Retry-After}). Lần bị từ
     * chối không được tính.
     *
     * @param subject các thành phần định danh (email, IP, id người dùng…) — được băm trước khi làm khóa
     */
    public void acquire(RateLimitPolicy policy, String... subject) {
        RateLimitProperties.Rule rule = properties.ruleFor(policy);
        List<?> result;
        try {
            result = redis.execute(
                    script,
                    List.of(keyOf(policy, subject)),
                    Long.toString(clock.millis()),
                    Long.toString(rule.window().toMillis()),
                    Integer.toString(rule.limit()),
                    UUID.randomUUID().toString());
        } catch (DataAccessException e) {
            log.error("Redis không khả dụng — bỏ qua giới hạn {} (fail-open)", policy, e);
            return;
        }
        if (result == null || result.size() != 2) {
            log.error("Kết quả script rate limit bất thường cho {}: {} — cho qua", policy, result);
            return;
        }
        if (((Number) result.get(0)).longValue() == 0) {
            long retryAfterMs = ((Number) result.get(1)).longValue();
            throw new RateLimitExceededException(Duration.ofMillis(retryAfterMs));
        }
    }

    /** Xóa bộ đếm — dùng khi đăng nhập đúng (U2 đếm lần <b>sai</b>). Redis sập thì bỏ qua. */
    public void reset(RateLimitPolicy policy, String... subject) {
        try {
            redis.delete(keyOf(policy, subject));
        } catch (DataAccessException e) {
            log.error("Redis không khả dụng — không xóa được bộ đếm {}", policy, e);
        }
    }

    private static String loadScript() {
        try {
            return new ClassPathResource(SCRIPT).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được " + SCRIPT, e);
        }
    }

    private static String keyOf(RateLimitPolicy policy, String... subject) {
        return KEY_PREFIX + policy.name().toLowerCase(Locale.ROOT) + ":"
                + TokenHasher.sha256Hex(String.join("\u0000", subject));
    }
}
