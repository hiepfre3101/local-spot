package com.localspot.event;

/**
 * Nội dung tính điểm đóng góp của {@code userId} vừa đổi (vote hữu ích nhận được, check-in, ảnh review xử lý xong, đề
 * xuất địa điểm được duyệt). Review đổi trạng thái dùng sẵn {@link ReviewChangedEvent}. Listener tính lại điểm + trao huy
 * hiệu sau commit — mất sự kiện thì job đêm đối soát (như rating, E9).
 */
public record ContributionChangedEvent(long userId) {}
