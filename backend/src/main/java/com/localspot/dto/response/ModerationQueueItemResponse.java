package com.localspot.dto.response;

import com.localspot.entity.ModerationItemType;
import com.localspot.entity.ReportTargetType;
import java.time.Instant;
import java.util.List;

/**
 * openapi {@code ModerationQueueItem} — một dòng hàng chờ gộp. Xử lý qua endpoint {@code decision} của loại tương ứng.
 *
 * @param position thứ tự xử lý (1 = cũ nhất) trong bộ lọc hiện tại
 * @param id id place / review / claim; với REPORT là id đối tượng bị báo cáo (nhóm theo {@code reportTargetType + id})
 * @param title tên địa điểm (PLACE, CLAIM), "địa điểm — trích đoạn" (REVIEW), mô tả đối tượng bị báo cáo (REPORT);
 *     {@code null} khi đối tượng bị báo cáo đã xóa
 * @param submittedBy tên người gửi — nhóm báo cáo: người báo cáo đầu tiên
 * @param flags {@code IP_FLAGGED} / {@code LOW_TRUST} — chỉ với REVIEW, lý do review bị giữ lại (FR-23)
 * @param reportCount số báo cáo đang mở, chỉ với REPORT
 */
public record ModerationQueueItemResponse(
        long position,
        ModerationItemType type,
        Long id,
        ReportTargetType reportTargetType,
        String title,
        String submittedBy,
        Instant submittedAt,
        List<Flag> flags,
        Integer reportCount) {

    public ModerationQueueItemResponse {
        flags = List.copyOf(flags);
    }

    public enum Flag {
        IP_FLAGGED,
        LOW_TRUST
    }
}
