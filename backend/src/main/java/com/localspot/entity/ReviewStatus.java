package com.localspot.entity;

/** Trạng thái đánh giá. Chỉ {@code PUBLISHED} được tính vào điểm xếp hạng. */
public enum ReviewStatus {
    PENDING,
    PUBLISHED,
    REJECTED,
    HIDDEN
}
