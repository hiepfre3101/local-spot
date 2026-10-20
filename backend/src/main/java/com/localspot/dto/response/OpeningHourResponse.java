package com.localspot.dto.response;

/** openapi {@code OpeningHour}: ISO 1 = Thứ Hai; giờ dạng {@code HH:mm}. */
public record OpeningHourResponse(int dayOfWeek, String openTime, String closeTime) {}
