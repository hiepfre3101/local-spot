package com.localspot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** openapi {@code AmenityRequest} — PUT thay toàn bộ, {@code icon} rỗng = bỏ icon. */
public record AmenityRequest(
        @NotBlank(message = "Tên tiện ích không được để trống.") @Size(max = 100, message = "Tên tối đa 100 ký tự.")
        String name,

        @NotBlank(message = "Slug không được để trống.")
        @Size(max = 120, message = "Slug tối đa 120 ký tự.")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang.")
        String slug,

        @Size(max = 100, message = "Icon tối đa 100 ký tự.") String icon) {}
