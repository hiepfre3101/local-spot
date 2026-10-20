package com.localspot.service;

/**
 * Các bản ảnh sinh ra từ một ảnh gốc (openapi {@code Photo.thumbUrl / mediumUrl / largeUrl}). Key object =
 * {@code storage_key + suffix} — CSDL chỉ lưu khóa gốc, URL từng bản suy ra (database.md {@code place_photos}).
 */
public enum PhotoSize {
    THUMB("-thumb.jpg"),
    MEDIUM("-medium.jpg"),
    LARGE("-large.jpg");

    private final String suffix;

    PhotoSize(String suffix) {
        this.suffix = suffix;
    }

    public String keyOf(String storageKey) {
        return storageKey + suffix;
    }
}
