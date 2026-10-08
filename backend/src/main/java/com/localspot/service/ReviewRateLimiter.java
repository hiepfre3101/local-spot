package com.localspot.service;

import com.localspot.config.ReviewProperties;
import com.localspot.exception.RateLimitExceededException;
import com.localspot.repository.ReviewRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Giới hạn N review / cửa sổ trượt mỗi tài khoản (FR-23: 5 / 24 giờ). Đếm trong bảng {@code reviews} — vẫn hiệu lực khi
 * Redis sập (chốt 2026-10-04) — và đếm cả review đã xóa: xóa không trả lại lượt. Người gọi phải khóa dòng người dùng
 * trước ({@code UserRepository.findByIdForUpdate}) để hai request đồng thời không cùng thấy "còn lượt".
 */
@Component
public class ReviewRateLimiter {

    private final ReviewRepository reviews;
    private final ReviewProperties properties;

    public ReviewRateLimiter(ReviewRepository reviews, ReviewProperties properties) {
        this.reviews = reviews;
        this.properties = properties;
    }

    /** Vượt giới hạn → 429 kèm {@code Retry-After} = lúc review cũ nhất trong cửa sổ rời cửa sổ. */
    public void check(Long userId, Instant now) {
        Instant since = now.minus(properties.window());
        if (reviews.countWrittenSince(userId, since) < properties.maxPerWindow()) {
            return;
        }
        Instant oldest = reviews.findOldestWrittenEpochMillisSince(userId, since)
                .map(Instant::ofEpochMilli)
                .orElse(now);
        throw new RateLimitExceededException(Duration.between(now, oldest.plus(properties.window())));
    }
}
