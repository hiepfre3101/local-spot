package com.localspot.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * openapi {@code CheckInRequest} — tọa độ và độ chính xác lấy từ Geolocation API của trình duyệt. Ngưỡng
 * {@code accuracyM} kiểm ở service (cấu hình được, lỗi có mã riêng {@code GPS_INACCURATE}).
 */
public record CheckInRequest(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @NotNull @PositiveOrZero Double accuracyM) {}
