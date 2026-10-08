package com.localspot.event;

import com.localspot.entity.ReviewStatus;

/**
 * Review vừa được tạo / sửa / xóa / đổi trạng thái theo cách làm đổi tập review PUBLISHED của địa điểm — điểm Bayesian
 * của {@code placeId} cần tính lại (FR-22). Listener tính lại rating gắn ở mục "Tính lại rating" (AFTER_COMMIT +
 * {@code @Async}); hiện chưa có listener.
 *
 * <p>Gộp các sự kiện {@code ReviewCreatedEvent} / {@code ReviewStatusChangedEvent} của sơ đồ lớp làm một: listener nào
 * cũng chỉ cần "địa điểm này phải tính lại", và tính lại luôn đọc dữ liệu hiện tại.
 *
 * @param before trạng thái trước thay đổi ({@code null} khi vừa tạo)
 * @param after trạng thái sau ({@code null} khi vừa xóa)
 */
public record ReviewChangedEvent(long reviewId, long placeId, ReviewStatus before, ReviewStatus after) {}
