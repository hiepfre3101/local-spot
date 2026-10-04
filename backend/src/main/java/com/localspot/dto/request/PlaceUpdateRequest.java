package com.localspot.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * openapi {@code PlaceUpdateRequest} — chủ địa điểm sửa thông tin (FR-32, UC24), ngữ nghĩa PATCH: trường {@code null} /
 * vắng = giữ nguyên. Xóa mô tả / điện thoại / website bằng chuỗi rỗng; {@code openingHours} / {@code amenityIds} gửi
 * lên là <b>thay toàn bộ</b> (mảng rỗng = xóa hết). Không có tên, vị trí, danh mục: chủ không "dời" được địa điểm để
 * thoát review — muốn đổi phải báo cáo WRONG_INFO. {@code version} (tùy chọn) lệch → 409.
 */
public record PlaceUpdateRequest(
        @Size(max = 5000) String description,
        @Size(max = 300) String address,
        @PositiveOrZero Integer priceMin,
        @PositiveOrZero Integer priceMax,

        @Size(max = 20) @Pattern(regexp = PlaceCreateRequest.PHONE, message = "số điện thoại không hợp lệ")
        String phone,

        @Size(max = 300) @Pattern(regexp = PlaceCreateRequest.WEBSITE, message = "phải là địa chỉ http(s)://")
        String website,

        @Size(max = 28) List<@NotNull @Valid OpeningHourRequest> openingHours,
        @Size(max = 50) List<@NotNull @Positive Long> amenityIds,
        Integer version) {}
