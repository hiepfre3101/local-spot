package com.localspot.dto.request;

import com.localspot.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** openapi {@code RegisterRequest} (FR-01). Chỉ nhận đúng các trường này — chặn mass assignment (role, trust...). */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 191) String email,
        @ValidPassword String password,
        @NotBlank @Size(min = 2, max = 100) String displayName) {}
