package com.localspot.dto.response;

import com.localspot.entity.ReviewStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * openapi {@code Review}. {@code rejectReason} chỉ có khi người xem là tác giả; {@code votedByMe} luôn {@code false}
 * với khách. {@code authorCheckedIn}: tác giả đã check-in tại địa điểm vào ngày viết hoặc trước đó (nhãn P04).
 * {@code place}: tên + slug để danh sách ngoài trang địa điểm (trang cá nhân P06) hiện và dẫn tới địa điểm.
 */
public record ReviewResponse(
        Long id,
        Long placeId,
        PlaceRef place,
        UserSummary author,
        int rating,
        String content,
        LocalDate visitedAt,
        ReviewStatus status,
        String rejectReason,
        List<PhotoResponse> photos,
        int helpfulCount,
        boolean votedByMe,
        boolean authorCheckedIn,
        long commentCount,
        OwnerReplyResponse ownerReply,
        Instant createdAt,
        Instant updatedAt,
        int version) {

    public ReviewResponse {
        photos = List.copyOf(photos);
    }
}
