package com.localspot.repository;

/** Kết quả truy vấn không gian: id địa điểm + khoảng cách (mét, làm tròn) tới tâm tìm kiếm. */
public record PlaceDistance(long placeId, int distanceM) {}
