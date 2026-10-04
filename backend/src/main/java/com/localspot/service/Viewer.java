package com.localspot.service;

/**
 * Người đang xem một trang công khai: {@code userId == null} là khách. {@code moderator} = có quyền
 * {@code place:approve} — controller đọc từ authority để service không phụ thuộc Spring Security.
 */
public record Viewer(Long userId, boolean moderator) {

    public static final Viewer ANONYMOUS = new Viewer(null, false);

    public boolean is(Long otherUserId) {
        return userId != null && userId.equals(otherUserId);
    }
}
