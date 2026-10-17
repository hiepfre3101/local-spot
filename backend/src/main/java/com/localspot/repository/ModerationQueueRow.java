package com.localspot.repository;

import com.localspot.entity.ModerationItemType;
import com.localspot.entity.ReportTargetType;
import java.time.Instant;

/**
 * Một mục hàng chờ gộp — kết quả {@link ModerationQueueQueries#page}.
 *
 * @param itemId id của place / review / claim; với REPORT là id đối tượng bị báo cáo
 * @param sortId khóa phụ của keyset, duy nhất trong loại: id bản ghi, với REPORT là id báo cáo đầu tiên của nhóm
 * @param submitterId người đề xuất / tác giả / người yêu cầu / người báo cáo đầu tiên
 * @param reportCount số báo cáo đang mở (chỉ REPORT, loại khác = 0)
 */
public record ModerationQueueRow(
        ModerationItemType type,
        Long itemId,
        ReportTargetType reportTargetType,
        long sortId,
        Instant submittedAt,
        Long submitterId,
        int reportCount,
        boolean ipFlagged) {

    public ModerationQueueKey key() {
        return new ModerationQueueKey(submittedAt, type, sortId);
    }
}
