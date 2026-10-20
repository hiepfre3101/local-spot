package com.localspot.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * openapi {@code ReviewUpdateRequest} — ngữ nghĩa PATCH: trường {@code null} = giữ nguyên. {@code version} (tùy chọn)
 * lệch → 409 như sửa địa điểm.
 */
public record ReviewUpdateRequest(
        @Min(value = 1, message = "Hãy chấm từ 1 đến 5 sao.") @Max(value = 5, message = "Hãy chấm từ 1 đến 5 sao.")
        Integer rating,

        @Size(max = 5000, message = "Nội dung tối đa 5000 ký tự.")
        String content,

        LocalDate visitedAt,
        Integer version) {}
