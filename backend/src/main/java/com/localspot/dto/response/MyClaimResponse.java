package com.localspot.dto.response;

import com.localspot.entity.ClaimStatus;
import java.time.Instant;

/**
 * openapi {@code MyClaim} — yêu cầu xác nhận sở hữu của chính người gửi ({@code GET /me/claims}, P09 — thêm 2026-10-18).
 * Không có số điện thoại / minh chứng / người duyệt: người gửi đã biết những gì mình gửi, tên kiểm duyệt viên không lộ ra
 * ngoài. {@code rejectReason} chỉ có khi bị từ chối.
 */
public record MyClaimResponse(
        Long id, PlaceRef place, ClaimStatus status, String rejectReason, Instant createdAt, Instant reviewedAt) {}
