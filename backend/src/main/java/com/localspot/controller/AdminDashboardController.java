package com.localspot.controller;

import com.localspot.dto.response.ActivityLogEntryResponse;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.DashboardResponse;
import com.localspot.security.Permissions;
import com.localspot.service.ActivityLogService;
import com.localspot.service.AdminDashboardService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/admin/dashboard}, {@code /api/v1/admin/activity-log} — thống kê (UC33, FR-41) và nhật ký (FR-42). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminDashboardController {

    private final AdminDashboardService dashboard;
    private final ActivityLogService activityLog;

    public AdminDashboardController(AdminDashboardService dashboard, ActivityLogService activityLog) {
        this.dashboard = dashboard;
        this.activityLog = activityLog;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_VIEW + "')")
    public DashboardResponse dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboard.dashboard(from, to);
    }

    @GetMapping("/activity-log")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_VIEW + "')")
    public CursorPage<ActivityLogEntryResponse> activityLog(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return activityLog.page(actorId, action, cursor, limit);
    }
}
