package com.localspot.service;

import com.localspot.entity.User;
import com.localspot.repository.UserViolationRepository;
import java.time.Clock;
import java.time.Instant;
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
        return calculator.calculate(new TrustScoreCalculator.Inputs(
                user.isEmailVerified(),
                user.getCreatedAt(),
                user.getHelpfulVotesCount(),
                violations.sumActivePoints(user.getId(), now),
                now));
    }
}
