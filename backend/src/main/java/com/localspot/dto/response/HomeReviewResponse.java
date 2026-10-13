package com.localspot.dto.response;

import java.time.Instant;

/**
 * openapi {@code HomeReview} — đánh giá nổi bật trên trang chủ (ReviewCard): đoạn trích ≤ 180 ký tự, không phải toàn văn.
 */
public record HomeReviewResponse(
        Long id, String excerpt, int rating, int helpfulCount, UserSummary author, PlaceRef place, Instant createdAt) {}
