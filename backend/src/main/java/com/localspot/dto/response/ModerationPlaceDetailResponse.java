package com.localspot.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * openapi {@code ModerationPlaceDetail} — màn duyệt một địa điểm (UC27, thêm 2026-10-19): chi tiết đầy đủ như trang công
 * khai (mô tả, ảnh kể cả ảnh chưa xử lý xong, giờ mở cửa, tiện ích), người đề xuất, thời điểm gửi và địa điểm nghi trùng
 * (chỉ khi còn PENDING — cùng luật với hàng chờ).
 */
public record ModerationPlaceDetailResponse(
        PlaceDetailResponse place,
        UserSummary proposer,
        Instant submittedAt,
        List<PlaceSummaryResponse> possibleDuplicates) {

    public ModerationPlaceDetailResponse {
        possibleDuplicates = List.copyOf(possibleDuplicates);
    }
}
