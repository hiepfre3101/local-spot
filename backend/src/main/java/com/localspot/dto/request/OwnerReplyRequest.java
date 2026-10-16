package com.localspot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** openapi body của {@code PUT /reviews/{reviewId}/reply} (FR-33). */
public record OwnerReplyRequest(@NotBlank @Size(max = 2000) String content) {}
