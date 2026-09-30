package com.localspot.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;

/**
 * {@link DayOfWeek} ↔ số ISO 1–7 (1 = Thứ Hai) như CHECK của {@code opening_hours.day_of_week}. Không dùng
 * {@code @Enumerated(ORDINAL)} vì ordinal bắt đầu từ 0 — lệch một ngày mà không báo lỗi.
 */
@Converter
public class IsoDayOfWeekConverter implements AttributeConverter<DayOfWeek, Integer> {

    @Override
    public Integer convertToDatabaseColumn(DayOfWeek day) {
        return day == null ? null : day.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Integer value) {
        return value == null ? null : DayOfWeek.of(value);
    }
}
