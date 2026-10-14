package com.localspot.service;

import com.localspot.dto.request.ReportCreateRequest;
import com.localspot.entity.Report;
import com.localspot.entity.ReportReason;
import com.localspot.entity.ReportTargetType;
import com.localspot.entity.User;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.ReportRepository;
import com.localspot.repository.UserRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thành viên báo cáo vi phạm (UC17, FR-25) — địa điểm, review, bình luận hoặc người dùng (quan hệ đa hình). Luật chốt
 * 2026-10-14:
 *
 * <ul>
 *   <li>Cần email đã xác thực — cùng cổng với viết review / bình luận, chặn tài khoản rác làm ngập hàng đợi.
 *   <li>{@code detail} bắt buộc với {@code WRONG_INFO} / {@code OTHER}: kiểm duyệt viên cần biết sai chỗ nào.
 *   <li>Chỉ báo cáo được nội dung đang hiện công khai ({@link ReportTargets#requireVisible}).
 *   <li>Không báo cáo review / bình luận của mình, không báo cáo chính mình. Địa điểm thì được — tên / vị trí / danh mục
 *       không ai sửa được (O5, O8), báo cáo {@code WRONG_INFO} là kênh duy nhất kể cả với người đề xuất.
 *   <li>Một báo cáo / người / đối tượng, kể cả khi báo cáo cũ đã xử lý (U10).
 * </ul>
 */
@Service
public class ReportService {

    private static final Set<ReportReason> DETAIL_REQUIRED = EnumSet.of(ReportReason.WRONG_INFO, ReportReason.OTHER);

    private final ReportRepository reports;
    private final UserRepository users;
    private final ReportTargets targets;

    public ReportService(ReportRepository reports, UserRepository users, ReportTargets targets) {
        this.reports = reports;
        this.users = users;
        this.targets = targets;
    }

    /**
     * Thứ tự kiểm tra: email đã xác thực (403) → {@code detail} theo lý do (422) → đối tượng đang hiện (404) → không
     * phải nội dung của mình (409) → chưa báo cáo (409; request chen tới được UNIQUE → 409 {@code DUPLICATE_RESOURCE}).
     */
    @Transactional
    public void create(Long reporterId, ReportCreateRequest request) {
        User reporter = users.findById(reporterId)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng."));
        if (!reporter.isEmailVerified()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, ErrorCode.EMAIL_NOT_VERIFIED, "Hãy xác thực email trước khi báo cáo.");
        }
        String detail = request.detail() == null ? null : request.detail().strip();
        if (detail != null && detail.isEmpty()) {
            detail = null;
        }
        if (detail == null && DETAIL_REQUIRED.contains(request.reason())) {
            throw ApiException.fieldError(
                    ErrorCode.VALIDATION_FAILED, "detail", "Hãy mô tả thông tin sai hoặc vấn đề bạn gặp.");
        }

        ReportTargetType type = request.targetType();
        User author = targets.requireVisible(type, request.targetId());
        if (type != ReportTargetType.PLACE && author.getId().equals(reporterId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.SELF_ACTION_FORBIDDEN,
                    type == ReportTargetType.USER
                            ? "Không thể báo cáo chính mình."
                            : "Không thể báo cáo nội dung của chính mình.");
        }
        if (reports.existsByReporterIdAndTargetTypeAndTargetId(reporterId, type, request.targetId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ErrorCode.REPORT_ALREADY_EXISTS, "Bạn đã báo cáo nội dung này rồi.");
        }
        reports.save(new Report(reporter, type, request.targetId(), request.reason(), detail));
    }
}
