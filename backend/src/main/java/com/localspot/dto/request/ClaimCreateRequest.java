package com.localspot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** openapi phần {@code claim} của {@code POST /places/{placeId}/claims} (UC23). File minh chứng là phần {@code evidence}. */
public record ClaimCreateRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = PlaceCreateRequest.PHONE, message = "số điện thoại không hợp lệ")
        String contactPhone,

        @Size(max = 1000) String note) {}
