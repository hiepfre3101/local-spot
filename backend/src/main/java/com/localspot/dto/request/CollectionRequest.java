package com.localspot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * openapi {@code CollectionRequest} — tạo và sửa dùng chung (PATCH gửi lại đủ tên / mô tả / công khai như form).
 * {@code isPublic} vắng = riêng tư.
 */
public record CollectionRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        Boolean isPublic) {

    public boolean publicRequested() {
        return Boolean.TRUE.equals(isPublic);
    }
}
