package com.localspot.dto.response;

import java.time.Instant;

/** openapi {@code Badge}. {@code icon} là khóa trung tính — frontend ánh xạ sang bộ icon của design system. */
public record BadgeResponse(String code, String name, String description, String icon, Instant awardedAt) {}
