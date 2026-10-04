package com.localspot.dto.response;

/** openapi {@code UserSummary} — thông tin công khai tối thiểu của một người dùng (không có email). */
public record UserSummary(Long id, String displayName, String avatarUrl) {}
