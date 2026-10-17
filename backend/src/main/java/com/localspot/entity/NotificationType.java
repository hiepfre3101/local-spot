package com.localspot.entity;

/**
 * Loại thông báo (FR-27) — đúng danh sách sự kiện trong UC (supervisor chốt 2026-10-16: không thêm sự kiện ngoài UC).
 * Cột {@code notifications.type} lưu {@link #name()}; frontend dựng câu chữ theo loại + {@code data}.
 */
public enum NotificationType {
    /** Người đề xuất: địa điểm được duyệt (UC11, UC27). */
    PLACE_APPROVED,
    /** Người đề xuất: địa điểm bị từ chối, kèm lý do. */
    PLACE_REJECTED,
    /** Tác giả: review trong hàng chờ được duyệt (UC28). */
    REVIEW_PUBLISHED,
    /** Tác giả: review bị từ chối, kèm lý do (UC28 3a). */
    REVIEW_REJECTED,
    /** Chủ địa điểm: có review mới được đăng (UC12 bước 8, UC28 bước 4). */
    NEW_REVIEW_ON_MY_PLACE,
    /** Tác giả review: chủ địa điểm phản hồi (FR-33). */
    OWNER_REPLIED,
    /** Tác giả / người đề xuất / chủ: nội dung bị ẩn sau báo cáo (UC29 bước 5); báo cáo người dùng → người bị báo cáo. */
    REPORT_ACTION_TAKEN,
    /** Người báo cáo: báo cáo được xác nhận và nội dung đã bị ẩn (UC29 bước 5). */
    REPORT_RESOLVED,
    /** Kiểm duyệt viên: có yêu cầu sở hữu mới (UC23 bước 5). */
    CLAIM_SUBMITTED,
    /** Người yêu cầu: được duyệt — đã là chủ địa điểm (UC30). */
    CLAIM_APPROVED,
    /** Người yêu cầu: bị từ chối, kèm lý do (gồm tự từ chối khi người khác được duyệt). */
    CLAIM_REJECTED
}
