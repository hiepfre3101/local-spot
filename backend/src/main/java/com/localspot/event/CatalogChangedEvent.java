package com.localspot.event;

/** Danh mục / tiện ích vừa đổi trong transaction hiện tại → xóa cache tương ứng sau commit ({@code CatalogCacheInvalidator}). */
public record CatalogChangedEvent(String cacheName) {}
