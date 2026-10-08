package com.localspot.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * openapi {@code ReviewCreateRequest} — phần {@code review} của multipart. Nội dung ≥ 20 ký tự (U4) tính sau khi bỏ
 * khoảng trắng hai đầu và "ngày đã đến ≤ hôm nay" theo giờ Việt Nam — kiểm ở service.
 */
public record ReviewCreateRequest(
        @NotNull(message = "Hãy chấm từ 1 đến 5 sao.")
        @Min(value = 1, message = "Hãy chấm từ 1 đến 5 sao.")
        @Max(value = 5, message = "Hãy chấm từ 1 đến 5 sao.")
        Integer rating,

        @NotBlank(message = "Nội dung không được để trống.") @Size(max = 5000, message = "Nội dung tối đa 5000 ký tự.")
        String content,

        @NotNull(message = "Hãy chọn ngày đã đến.") LocalDate visitedAt) {}
