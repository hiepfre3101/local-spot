package com.localspot.repository;

/** Tổng điểm phạt còn hiệu lực của một người dùng — kết quả {@link UserViolationRepository#sumActivePointsByUserIds}. */
public record UserActivePoints(Long userId, Long points) {}
