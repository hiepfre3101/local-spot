package com.localspot.repository;

/**
 * v và R của một địa điểm (FR-22).
 *
 * @param reviewCount số review PUBLISHED
 * @param average trung bình sao — {@code null} khi chưa có review
 */
public record PlaceRatingStats(long reviewCount, Double average) {}
