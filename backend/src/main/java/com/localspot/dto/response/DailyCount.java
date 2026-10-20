package com.localspot.dto.response;

import java.time.LocalDate;

/** Một điểm của chuỗi theo ngày (giờ Việt Nam). Chuỗi luôn đủ mọi ngày trong khoảng — ngày không có gì = 0. */
public record DailyCount(LocalDate date, long count) {}
