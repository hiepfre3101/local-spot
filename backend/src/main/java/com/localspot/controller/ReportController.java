package com.localspot.controller;

import com.localspot.dto.request.ReportCreateRequest;
import com.localspot.security.AuthenticatedUser;
import com.localspot.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/v1/reports} — thành viên báo cáo vi phạm (UC17, FR-25). */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ReportCreateRequest request) {
        reports.create(user.id(), request);
    }
}
