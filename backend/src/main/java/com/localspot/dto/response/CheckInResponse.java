package com.localspot.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/**
 * openapi {@code CheckIn}. Không trả tọa độ người dùng gửi lên (chỉ lưu để giải trình) — kể cả cho chính chủ, vì lịch sử
 * check-in hiển thị theo địa điểm, không theo vị trí.
 */
public record CheckInResponse(
        Long id, Long placeId, PlaceSummaryResponse place, int distanceM, LocalDate checkinDate, Instant createdAt) {}
