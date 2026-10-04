package com.localspot.repository;

import com.localspot.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Nhật ký thao tác quản trị (FR-42) — chỉ thêm, ghi qua {@code ActivityLogAspect}. */
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {}
