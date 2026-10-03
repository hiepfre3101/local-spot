package com.localspot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** openapi {@code TokenRequest} — token lấy từ link email. */
public record TokenRequest(@NotBlank @Size(max = 100) String token) {}
