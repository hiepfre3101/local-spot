package com.localspot.repository;

import java.math.BigDecimal;

/**
 * Vị trí dòng cuối của trang trước theo {@link PlaceSort}: {@code value} là điểm Bayesian (SCORE) hoặc số review
 * (MOST_REVIEWED), {@code null} với NEWEST (chỉ cần id).
 */
public record PlaceKeyset(PlaceSort sort, BigDecimal value, long id) {}
