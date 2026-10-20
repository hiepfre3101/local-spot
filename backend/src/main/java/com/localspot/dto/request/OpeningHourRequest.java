package com.localspot.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * openapi {@code OpeningHour}: ISO 1 = Thứ Hai … 7 = Chủ nhật, giờ dạng {@code HH:mm}. {@code closeTime < openTime} = mở
 * qua đêm; trùng nhau bị từ chối ở service (không rõ là đóng hay mở 24 giờ — mở cả ngày ghi {@code 00:00–23:59}).
 */
public record OpeningHourRequest(
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,

        @NotNull @Pattern(regexp = HHMM, message = "phải có dạng HH:mm")
        String openTime,

        @NotNull @Pattern(regexp = HHMM, message = "phải có dạng HH:mm")
        String closeTime) {

    static final String HHMM = "^([01]\\d|2[0-3]):[0-5]\\d$";
}
