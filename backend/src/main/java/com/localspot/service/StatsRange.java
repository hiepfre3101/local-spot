package com.localspot.service;

import com.localspot.dto.response.DailyCount;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Khoảng ngày {@code [from, to]} (cả hai đầu, giờ Việt Nam) của các màn thống kê. Bỏ trống {@code to} = hôm nay, bỏ
 * trống {@code from} = lùi {@code defaultDays - 1} ngày từ {@code to}. Tối đa {@value #MAX_DAYS} ngày để chuỗi theo ngày
 * có độ dài chặn trên; {@code from > to} hay quá dài → 422 thay vì âm thầm sửa.
 */
record StatsRange(LocalDate from, LocalDate to) {

    static final int MAX_DAYS = 366;
    static final ZoneId ZONE = PlaceService.VIEW_DATE_ZONE;

    static StatsRange of(LocalDate from, LocalDate to, int defaultDays, Clock clock) {
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(ZONE));
        LocalDate start = from != null ? from : end.minusDays(defaultDays - 1L);
        if (start.isAfter(end)) {
            throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "from", "from phải trước hoặc bằng to.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw ApiException.fieldError(
                    ErrorCode.VALIDATION_FAILED, "from", "Khoảng thống kê tối đa " + MAX_DAYS + " ngày.");
        }
        return new StatsRange(start, end);
    }

    long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** Kỳ liền trước, cùng số ngày. */
    StatsRange previous() {
        return new StatsRange(from.minusDays(days()), from.minusDays(1));
    }

    /** 00:00 ngày {@code from} giờ Việt Nam, theo UTC. */
    Instant start() {
        return from.atStartOfDay(ZONE).toInstant();
    }

    /** 00:00 ngày sau {@code to} — mốc mở của {@code [start, end)}. */
    Instant end() {
        return to.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    /** Chuỗi đủ mọi ngày trong khoảng, ngày không có trong {@code counts} = 0 — frontend vẽ thẳng, không tự điền. */
    List<DailyCount> series(Map<LocalDate, Long> counts) {
        List<DailyCount> series = new ArrayList<>((int) days());
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            series.add(new DailyCount(day, counts.getOrDefault(day, 0L)));
        }
        return series;
    }
}
