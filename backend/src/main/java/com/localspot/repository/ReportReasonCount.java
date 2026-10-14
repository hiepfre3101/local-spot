package com.localspot.repository;

import com.localspot.entity.ReportReason;
import com.localspot.entity.ReportTargetType;

/** Số báo cáo đang mở theo lý do của một đối tượng — {@link ReportRepository#countOpenReasons}. */
public record ReportReasonCount(ReportTargetType targetType, Long targetId, ReportReason reason, long count) {}
