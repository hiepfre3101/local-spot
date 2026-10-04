package com.localspot.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * openapi {@code PlaceCreateRequest} — phần {@code place} (JSON) của {@code POST /places} multipart (FR-15, UC11). Chỉ
 * nhận đúng các trường này (chặn mass assignment: status, owner, điểm xếp hạng…). Chuỗi rỗng ở trường tùy chọn = không
 * có. Danh mục / tiện ích có tồn tại hay không và {@code priceMin ≤ priceMax} kiểm ở service.
 */
public record PlaceCreateRequest(
        @NotBlank @Size(min = 2, max = 200) String name,
        @NotNull @Positive Long categoryId,
        @Size(max = 5000) String description,
        @NotBlank @Size(max = 300) String address,
        @NotBlank @Size(max = 100) String city,
        @NotNull @Valid GeoPointRequest location,
        @PositiveOrZero Integer priceMin,
        @PositiveOrZero Integer priceMax,

        @Size(max = 20) @Pattern(regexp = PlaceCreateRequest.PHONE, message = "số điện thoại không hợp lệ")
        String phone,

        @Size(max = 300) @Pattern(regexp = PlaceCreateRequest.WEBSITE, message = "phải là địa chỉ http(s)://")
        String website,

        @Size(max = 28) List<@NotNull @Valid OpeningHourRequest> openingHours,
        @Size(max = 50) List<@NotNull @Positive Long> amenityIds,
        @Size(max = 10) List<@NotBlank @Size(max = 50) String> tags) {

    /** Chữ số, khoảng trắng và {@code + ( ) . -}; rỗng = không có. */
    static final String PHONE = "^$|^[0-9+()\\s.-]{6,20}$";
    /** Chỉ http(s) — chặn {@code javascript:} khi frontend hiển thị thành link. */
    static final String WEBSITE = "^$|^https?://\\S+$";
}
