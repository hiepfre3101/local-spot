package com.localspot.repository;

import com.localspot.entity.ReportTargetType;
import java.time.Instant;

/**
 * Một nhóm báo cáo đang mở về cùng một đối tượng — kết quả {@link ReportRepository#findOpenGroups}. {@code firstId} là
 * khóa keyset: id tăng theo thời gian gửi nên xếp theo nó là FIFO theo báo cáo đầu tiên.
 */
public record ReportGroupRow(
        ReportTargetType targetType, Long targetId, long reportCount, Instant firstReportedAt, Long firstId) {}
