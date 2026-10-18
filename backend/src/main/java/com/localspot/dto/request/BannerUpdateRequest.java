package com.localspot.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sửa mô tả / bật tắt ảnh một ô (UC34) — không đổi ảnh. {@code version} chống ghi đè lẫn nhau. */
public record BannerUpdateRequest(
        @Size(max = 200, message = "Mô tả ảnh tối đa 200 ký tự.")
        String altText,

        Boolean active,
        @NotNull(message = "Thiếu version.") Integer version) {}
