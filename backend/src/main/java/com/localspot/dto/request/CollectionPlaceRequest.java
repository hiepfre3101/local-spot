package com.localspot.dto.request;

import jakarta.validation.constraints.Size;

/** Body tùy chọn của {@code PUT /collections/{id}/places/{placeId}} — ghi chú cho địa điểm trong bộ. */
public record CollectionPlaceRequest(@Size(max = 500) String note) {}
