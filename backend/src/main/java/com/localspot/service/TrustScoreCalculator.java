package com.localspot.service;

import com.localspot.config.TrustProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Công thức trust score (requirements §5.1 — phần lõi chống review ảo, CLAUDE.md §3). Hàm thuần: không truy vấn, không
 * đọc đồng hồ, để kiểm thử từng dòng của bảng ví dụ trong requirements.
 *
 * <pre>
 * trust = clamp(w1 × age_score + w2 × helpful_score − w3 × penalty_score + base, 0, 100)
 *   base          = 10 nếu đã xác thực email, ngược lại 0
 *   age_score     = min(floor(số ngày tuổi / 7), 18)
 *   helpful_score = min(số vote hữu ích hợp lệ, 36)
 *   penalty_score = tổng điểm phạt của vi phạm còn hiệu lực (25 / vi phạm, hết hạn sau 30 ngày)
 * </pre>
 */
@Component
public class TrustScoreCalculator {

    static final int MIN = 0;
    static final int MAX = 100;

    private final TrustProperties p;

    public TrustScoreCalculator(TrustProperties properties) {
        this.p = properties;
    }

    public int calculate(Inputs in) {
        long ageDays =
                Math.max(0, Duration.between(in.accountCreatedAt(), in.now()).toDays());
        long ageScore = Math.min(ageDays / p.ageDaysPerPoint(), p.ageCap());
        long helpfulScore = Math.min(Math.max(0, in.validHelpfulVotes()), p.helpfulCap());
        long base = in.emailVerified() ? p.base() : 0;

        long raw = p.ageWeight() * ageScore
                + p.helpfulWeight() * helpfulScore
                - p.penaltyWeight() * Math.max(0, in.activePenaltyPoints())
                + base;
        return (int) Math.clamp(raw, MIN, MAX);
    }

    /** Đủ điều kiện đăng review ngay, không qua hàng chờ duyệt (FR-23, FR-36). */
    public boolean canPublishWithoutModeration(int trustScore) {
        return trustScore >= p.publishThreshold();
    }

    /**
     * @param validHelpfulVotes {@code users.helpful_votes_count} — chỉ vote có {@code counted = 1}
     * @param activePenaltyPoints tổng {@code user_violations.points} có {@code expires_at > now}
     */
    public record Inputs(
            boolean emailVerified,
            Instant accountCreatedAt,
            int validHelpfulVotes,
            long activePenaltyPoints,
            Instant now) {}
}
