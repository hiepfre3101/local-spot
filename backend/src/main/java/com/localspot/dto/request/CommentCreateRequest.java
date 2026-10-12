package com.localspot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * openapi {@code CommentCreateRequest} (FR-21). {@code parentId = null}: bình luận gốc; khác null: trả lời — phải là
 * bình luận gốc đang hiện của cùng review (2 cấp, Q5), kiểm ở service.
 */
public record CommentCreateRequest(
        @NotBlank(message = "Nội dung bình luận không được để trống.")
        @Size(max = 2000, message = "Bình luận tối đa 2000 ký tự.")
        String content,

        Long parentId) {}
