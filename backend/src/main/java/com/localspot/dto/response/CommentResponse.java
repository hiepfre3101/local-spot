package com.localspot.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * openapi {@code Comment} (FR-21). {@code replies}: chỉ có ở bình luận gốc (trả lời là {@code null}).
 *
 * <p>{@code deleted = true}: placeholder của bình luận gốc đã xóa / bị ẩn nhưng còn trả lời — {@code author} và
 * {@code content} là {@code null}, chỉ giữ khung để các trả lời còn ngữ cảnh (chốt 2026-10-11).
 */
public record CommentResponse(
        Long id,
        UserSummary author,
        String content,
        Long parentId,
        boolean deleted,
        List<CommentResponse> replies,
        Instant createdAt) {

    public CommentResponse {
        replies = replies == null ? null : List.copyOf(replies);
    }
}
