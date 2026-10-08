package com.localspot.dto.response;

import com.localspot.entity.ReviewStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * openapi {@code Review}. {@code rejectReason} chỉ có khi người xem là tác giả; {@code votedByMe} luôn {@code false}
 * với khách.
 */
public record ReviewResponse(
        Long id,
        Long placeId,
        UserSummary author,
        int rating,
        String content,
        LocalDate visitedAt,
        ReviewStatus status,
        String rejectReason,
        List<PhotoResponse> photos,
        int helpfulCount,
        boolean votedByMe,
        long commentCount,
        OwnerReplyResponse ownerReply,
        Instant createdAt,
        Instant updatedAt,
        int version) {

    public ReviewResponse {
        photos = List.copyOf(photos);
    }
}
