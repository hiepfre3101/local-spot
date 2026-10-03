package com.localspot.dto.response;

/** openapi {@code AuthResponse}. Refresh token KHÔNG nằm ở body mà trong cookie HttpOnly (S1). */
public record AuthResponse(String accessToken, long expiresIn, MeResponse user) {}
