package com.localspot.config;

import com.localspot.security.RateLimitPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code localspot.rate-limit.policies.*} — ngưỡng giới hạn tần suất (NFR-10). Giá trị đã chốt nằm ở application.yml;
 * thiếu ngưỡng cho một {@link RateLimitPolicy} là lỗi cấu hình → từ chối khởi động thay vì âm thầm không giới hạn.
 */
@Validated
@ConfigurationProperties("localspot.rate-limit")
public record RateLimitProperties(@NotNull Map<RateLimitPolicy, @Valid Rule> policies) {

    public RateLimitProperties {
        policies = Map.copyOf(policies);
        Set<RateLimitPolicy> missing = EnumSet.allOf(RateLimitPolicy.class);
        missing.removeAll(policies.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Thiếu cấu hình localspot.rate-limit.policies cho: " + missing);
        }
    }

    public Rule ruleFor(RateLimitPolicy policy) {
        return policies.get(policy);
    }

    /** Tối đa {@code limit} lần trong mọi khoảng {@code window} liên tiếp (cửa sổ trượt). */
    public record Rule(@Positive int limit, @NotNull Duration window) {}
}
