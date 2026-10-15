package com.localspot.dto.response;

import java.util.List;

/**
 * openapi {@code ReportGroupDetail} — màn xử lý báo cáo (sitemap {@code /admin/moderation/reports/:targetType/:targetId}).
 *
 * @param author người chịu trách nhiệm nội dung — người đề xuất địa điểm, tác giả review / bình luận, hoặc chính người
 *     bị báo cáo; {@code null} khi đối tượng đã bị xóa. Là người sẽ bị ghi vi phạm nếu ẩn (luật O9 với địa điểm)
 * @param reports các báo cáo đang mở, cũ trước
 */
public record ReportGroupDetailResponse(
        ReportGroupResponse group, UserSummary author, List<ReportItemResponse> reports) {}
