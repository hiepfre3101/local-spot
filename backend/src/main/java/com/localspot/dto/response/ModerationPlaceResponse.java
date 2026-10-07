package com.localspot.dto.response;

import java.util.List;

/**
 * openapi {@code ModerationPlace} — một mục trong hàng chờ duyệt địa điểm: thẻ địa điểm + các địa điểm nghi trùng (tên
 * gần giống trong 50 m, gồm cả đề xuất chờ của người khác; kèm {@code distanceM}). Chỉ hàng chờ PENDING có nghi trùng.
 */
public record ModerationPlaceResponse(PlaceSummaryResponse place, List<PlaceSummaryResponse> possibleDuplicates) {

    public ModerationPlaceResponse {
        possibleDuplicates = List.copyOf(possibleDuplicates);
    }
}
