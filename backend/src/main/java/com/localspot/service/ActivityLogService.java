package com.localspot.service;

import com.localspot.dto.response.ActivityLogEntryResponse;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.UserSummary;
import com.localspot.entity.ActivityLog;
import com.localspot.repository.ActivityLogRepository;
import com.localspot.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc nhật ký thao tác quản trị (FR-42) — {@code /admin/activity-log}. Ghi nằm ở {@code ActivityLogAspect}. */
@Service
public class ActivityLogService {

    private final ActivityLogRepository logs;
    private final UserRepository users;

    public ActivityLogService(ActivityLogRepository logs, UserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    /** Mới nhất trước; {@code action} so khớp chính xác (vd. {@code REVIEW_REJECT}), trống = mọi thao tác. */
    @Transactional(readOnly = true)
    public CursorPage<ActivityLogEntryResponse> page(Long actorId, String action, String cursor, Integer limit) {
        int pageSize = KeysetCursor.limit(limit);
        String actionFilter = action == null || action.isBlank() ? null : action.strip();
        List<ActivityLog> rows =
                logs.findPage(actorId, actionFilter, KeysetCursor.decode(cursor), Limit.of(pageSize + 1));
        return KeysetPages.byId(rows, pageSize, ActivityLog::getId, this::toEntries);
    }

    private List<ActivityLogEntryResponse> toEntries(List<ActivityLog> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        // getActor().getId() đọc khóa ngoại trên proxy, không nạp người dùng
        List<Long> actorIds =
                page.stream().map(log -> log.getActor().getId()).distinct().toList();
        Map<Long, UserSummary> actors = HashMap.newHashMap(actorIds.size());
        for (Object[] row : users.findSummaryRowsIncludingDeleted(actorIds)) {
            long id = ((Number) row[0]).longValue();
            actors.put(id, new UserSummary(id, (String) row[1], (String) row[2]));
        }
        return page.stream()
                .map(log -> new ActivityLogEntryResponse(
                        log.getId(),
                        actors.get(log.getActor().getId()),
                        log.getAction(),
                        log.getTargetType(),
                        log.getTargetId(),
                        log.getMetadata(),
                        log.getCreatedAt()))
                .toList();
    }
}
