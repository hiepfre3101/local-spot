package com.localspot.dto.response;

/** openapi {@code Photo} — URL các kích thước suy ra từ {@code storage_key}, chỉ có khi ảnh đã xử lý xong (READY). */
public record PhotoResponse(Long id, String status, String thumbUrl, String mediumUrl, String largeUrl) {}
