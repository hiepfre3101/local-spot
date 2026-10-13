package com.localspot.dto.response;

/** openapi {@code LeaderboardEntry} — hạng kiểu thi đấu (cùng điểm cùng hạng). */
public record LeaderboardEntryResponse(int rank, UserSummary user, int points) {}
