package com.localspot.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * openapi {@code LoginRequest} (FR-03). Không áp luật định dạng mật khẩu ở đây: sai định dạng cũng chỉ là "sai thông
 * tin đăng nhập", không tiết lộ luật cho người dò.
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
