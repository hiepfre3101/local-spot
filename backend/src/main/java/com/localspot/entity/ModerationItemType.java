package com.localspot.entity;

/**
 * Loại việc trong hàng chờ gộp {@code /moderation/queue}. Thứ tự khai báo là khóa phụ khi hai mục gửi cùng thời điểm —
 * đổi thứ tự làm cursor cũ trỏ sai chỗ.
 */
public enum ModerationItemType {
    PLACE,
    REVIEW,
    REPORT,
    CLAIM
}
