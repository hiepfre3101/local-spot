package com.localspot.service;

import com.localspot.entity.User;
import com.localspot.repository.UserActivePoints;
import com.localspot.repository.UserViolationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gom dữ liệu đầu vào và gọi {@link TrustScoreCalculator}. Trust không lưu cột (database.md D1): tuổi tài khoản tăng và
 * vi phạm hết hạn theo thời gian, cột lưu sẵn sẽ lỗi thời nếu không có job định kỳ.
 */
@Service
public class TrustScoreService {

    private final TrustScoreCalculator calculator;
    private final UserViolationRepository violations;
    private final Clock clock;

    public TrustScoreService(TrustScoreCalculator calculator, UserViolationRepository violations, Clock clock) {
        this.calculator = calculator;
        this.violations = violations;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public int trustScoreOf(User user) {
        Instant now = clock.instant();
        return calculate(user, violations.sumActivePoints(user.getId(), now), now);
    }

    /** Trust của nhiều người dùng (id → điểm) với một truy vấn điểm phạt — cho danh sách, tránh N+1. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> trustScoresOf(Collection<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        Instant now = clock.instant();
        List<Long> ids = users.stream().map(User::getId).toList();
        Map<Long, Long> penalties = violations.sumActivePointsByUserIds(ids, now).stream()
                .collect(Collectors.toMap(UserActivePoints::userId, UserActivePoints::points));
        Map<Long, Integer> scores = HashMap.newHashMap(users.size());
        for (User user : users) {
            scores.put(user.getId(), calculate(user, penalties.getOrDefault(user.getId(), 0L), now));
        }
        return scores;
    }

    private int calculate(User user, long activePenaltyPoints, Instant now) {
        return calculator.calculate(new TrustScoreCalculator.Inputs(
                user.isEmailVerified(), user.getCreatedAt(), user.getHelpfulVotesCount(), activePenaltyPoints, now));
    }
}
