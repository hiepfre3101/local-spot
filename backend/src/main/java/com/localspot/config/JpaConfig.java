package com.localspot.config;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing điền {@code created_at} / {@code updated_at}. Tách khỏi lớp main để test slice (@WebMvcTest) không
 * kéo theo JPA.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    /** Đồng hồ UTC dùng chung — test thay bằng {@code Clock.fixed(...)} để kiểm soát thời gian. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Trả {@code Instant} (luôn UTC) thay vì {@code LocalDateTime} mặc định phụ thuộc múi giờ JVM. */
    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
