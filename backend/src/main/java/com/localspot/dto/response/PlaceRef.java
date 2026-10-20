package com.localspot.dto.response;

/** Tham chiếu tối thiểu tới một địa điểm (tên + slug để dựng liên kết {@code /places/{slug}}). */
public record PlaceRef(Long id, String slug, String name) {}
