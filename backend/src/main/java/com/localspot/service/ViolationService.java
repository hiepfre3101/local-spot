package com.localspot.service;

import com.localspot.config.ViolationProperties;
import com.localspot.entity.User;
import com.localspot.entity.UserViolation;
import com.localspot.entity.ViolationSourceType;
import com.localspot.repository.UserRepository;
import com.localspot.repository.UserViolationRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi một vi phạm vào penalty_score của trust (requirements §5.1). Một chỗ dùng chung cho hai nguồn: báo cáo được xác
 * nhận (UC29) và review bị từ chối (UC28). Trust không lưu cột (D1) nên không có gì khác cần
 * cập nhật: lần tính trust kế tiếp đã thấy vi phạm.
 */
@Service
public class ViolationService {

    private final UserViolationRepository violations;
    private final UserRepository users;
    private final ViolationProperties properties;
    private final Clock clock;

    public ViolationService(
            UserViolationRepository violations, UserRepository users, ViolationProperties properties, Clock clock) {
        this.violations = violations;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    /** Chạy trong transaction của thao tác kiểm duyệt — thao tác rollback thì vi phạm cũng không được ghi. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(User user, ViolationSourceType source, Long sourceId, Long moderatorId) {
        violations.save(new UserViolation(
                user,
                source,
                sourceId,
                properties.points(),
                clock.instant().plus(properties.ttl()),
                users.getReferenceById(moderatorId)));
    }
}
