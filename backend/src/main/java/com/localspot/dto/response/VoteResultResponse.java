package com.localspot.dto.response;

/** openapi {@code VoteResult} (FR-20) — số vote mới nhất trong CSDL và trạng thái vote của người gọi. */
public record VoteResultResponse(int helpfulCount, boolean votedByMe) {}
