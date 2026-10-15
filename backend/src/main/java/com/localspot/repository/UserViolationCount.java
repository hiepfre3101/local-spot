package com.localspot.repository;

/** Số vi phạm còn hiệu lực của một người dùng — kết quả {@link UserViolationRepository#countActiveByUserIds}. */
public record UserViolationCount(Long userId, Long count) {}
