package com.localspot.repository;

import com.localspot.entity.ModerationItemType;
import java.time.Instant;

/** Vị trí trong hàng chờ gộp — bộ ba sắp xếp {@code (submittedAt, type, sortId)}, cũng là nội dung cursor. */
public record ModerationQueueKey(Instant submittedAt, ModerationItemType type, long sortId) {}
