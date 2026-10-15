package com.localspot.service;

import com.localspot.audit.AuditedAction;
import com.localspot.dto.request.ReportDecisionRequest;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.ReportGroupDetailResponse;
import com.localspot.dto.response.ReportGroupResponse;
import com.localspot.dto.response.ReportItemResponse;
import com.localspot.dto.response.UserSummary;
import com.localspot.entity.Report;
import com.localspot.entity.ReportReason;
import com.localspot.entity.ReportStatus;
import com.localspot.entity.ReportTargetType;
import com.localspot.entity.User;
import com.localspot.entity.ViolationSourceType;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.mapper.CommunityMapper;
import com.localspot.repository.ReportGroupRow;
import com.localspot.repository.ReportReasonCount;
import com.localspot.repository.ReportRepository;
import com.localspot.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm duyệt viên xử lý báo cáo (UC29, FR-37). Hàng đợi gom theo đối tượng; một quyết định áp lên <b>mọi</b> báo cáo
 * đang mở về đối tượng đó. Luật:
 *
 * <ul>
 *   <li><b>Không sửa nội dung</b> (O8, chốt 2026-10-14): chỉ ẩn hoặc bỏ qua — tên / vị trí / danh mục địa điểm sai thì
 *       ẩn hoặc đóng báo cáo, là giới hạn đã biết.
 *   <li><b>Ai bị ghi vi phạm khi ẩn</b>: tác giả review / bình luận; người bị báo cáo; với địa điểm là <b>người đề xuất,
 *       chỉ khi lý do được xác nhận là SPAM / FAKE / OFFENSIVE</b> (O9) — {@code WRONG_INFO} / {@code OTHER} có thể là
 *       thông tin lỗi thời trung thực. Không bao giờ ghi cho chủ địa điểm hay người đã check-in.
 *   <li>Đối tượng đã bị xóa trước khi xử lý → báo cáo đóng RESOLVED, không ghi vi phạm (UC29 3c).
 *   <li>Không xử lý báo cáo về nội dung của chính mình, hay địa điểm mình sở hữu (409 {@code SELF_ACTION_FORBIDDEN}).
 *   <li>Hai kiểm duyệt viên cùng lúc: khóa các báo cáo đang mở → người sau 409 {@code REPORT_ALREADY_HANDLED}.
 * </ul>
 *
 * Mỗi quyết định ghi {@code activity_log} (FR-42) với loại / id của đối tượng bị báo cáo. Thông báo cho tác giả và người
 * báo cáo (UC29 bước 5) gắn vào đây ở mục thông báo.
 */
@Service
public class ReportModerationService {

    /** O9: lý do cho thấy người đề xuất cố ý gửi nội dung xấu. */
    private static final Set<ReportReason> PLACE_VIOLATION_REASONS =
            EnumSet.of(ReportReason.SPAM, ReportReason.FAKE, ReportReason.OFFENSIVE);

    /** Kết quả một quyết định — nguồn metadata của {@code activity_log}. */
    public record Outcome(int reportCount, boolean targetDeleted, boolean violationRecorded) {}

    private final ReportRepository reports;
    private final ReportTargets targets;
    private final ViolationService violations;
    private final UserRepository users;
    private final CommunityMapper mapper;
    private final Clock clock;

    public ReportModerationService(
            ReportRepository reports,
            ReportTargets targets,
            ViolationService violations,
            UserRepository users,
            CommunityMapper mapper,
            Clock clock) {
        this.reports = reports;
        this.targets = targets;
        this.violations = violations;
        this.users = users;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ─── Hàng đợi ───────────────────────────────────────────────────────────

    /** Nhóm báo cáo đang mở, FIFO theo báo cáo đầu tiên; ba truy vấn + một truy vấn mỗi loại đối tượng có trên trang. */
    @Transactional(readOnly = true)
    public CursorPage<ReportGroupResponse> queue(ReportTargetType targetType, String cursor, Integer limit) {
        int pageSize = KeysetCursor.limit(limit);
        Long afterId = KeysetCursor.decode(cursor);
        List<ReportGroupRow> rows = reports.findOpenGroups(targetType, afterId, Limit.of(pageSize + 1));
        return KeysetPages.byId(rows, pageSize, ReportGroupRow::firstId, this::toGroups);
    }

    private List<ReportGroupResponse> toGroups(List<ReportGroupRow> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        Map<GroupKey, Map<ReportReason, Integer>> reasons = new HashMap<>();
        for (ReportReasonCount count : reports.countOpenReasons(
                page.stream().map(ReportGroupRow::targetId).collect(Collectors.toSet()))) {
            reasons.computeIfAbsent(
                            new GroupKey(count.targetType(), count.targetId()),
                            key -> new EnumMap<>(ReportReason.class))
                    .put(count.reason(), Math.toIntExact(count.count()));
        }
        Map<ReportTargetType, Map<Long, ReportTargets.Target>> loaded = new EnumMap<>(ReportTargetType.class);
        page.stream()
                .collect(Collectors.groupingBy(
                        ReportGroupRow::targetType, Collectors.mapping(ReportGroupRow::targetId, Collectors.toSet())))
                .forEach((type, ids) -> loaded.put(type, targets.load(type, ids)));

        List<ReportGroupResponse> items = new ArrayList<>(page.size());
        for (ReportGroupRow row : page) {
            ReportTargets.Target target = loaded.get(row.targetType()).get(row.targetId());
            items.add(group(
                    row.targetType(),
                    row.targetId(),
                    target,
                    Math.toIntExact(row.reportCount()),
                    reasons.getOrDefault(new GroupKey(row.targetType(), row.targetId()), Map.of()),
                    row.firstReportedAt()));
        }
        return items;
    }

    /** Màn xử lý một đối tượng: các báo cáo đang mở (cũ trước) với mô tả của từng người + người sẽ bị ghi vi phạm. */
    @Transactional(readOnly = true)
    public ReportGroupDetailResponse detail(ReportTargetType targetType, Long targetId) {
        List<Report> open = reports.findOpenWithReporter(targetType, targetId);
        if (open.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, ErrorCode.REPORT_NOT_FOUND, "Không có báo cáo nào đang chờ về nội dung này.");
        }
        Optional<ReportTargets.Target> target = targets.find(targetType, targetId);
        Map<ReportReason, Integer> reasons = new EnumMap<>(ReportReason.class);
        List<ReportItemResponse> items = new ArrayList<>(open.size());
        for (Report report : open) {
            reasons.merge(report.getReason(), 1, Integer::sum);
            items.add(new ReportItemResponse(
                    report.getId(),
                    mapper.toUserSummary(report.getReporter()),
                    report.getReason(),
                    report.getDetail(),
                    report.getCreatedAt()));
        }
        // Tác giả nạp lại theo id: người đề xuất đã xóa tài khoản thì proxy không nạp được (@SQLRestriction)
        UserSummary author = target.flatMap(t -> users.findById(t.author().getId()))
                .map(mapper::toUserSummary)
                .orElse(null);
        ReportGroupResponse group = group(
                targetType,
                targetId,
                target.orElse(null),
                open.size(),
                reasons,
                open.getFirst().getCreatedAt());
        return new ReportGroupDetailResponse(group, author, items);
    }

    // ─── Quyết định ─────────────────────────────────────────────────────────

    /**
     * Xác nhận vi phạm: ẩn đối tượng, ghi vi phạm theo luật đầu lớp, đóng mọi báo cáo đang mở RESOLVED. Vi phạm trỏ về
     * báo cáo đầu tiên ({@code user_violations.source_id}) — một quyết định một vi phạm dù có bao nhiêu báo cáo.
     */
    @Transactional
    @AuditedAction(
            action = "REPORT_HIDE",
            targetType = "#request.targetType()",
            targetId = "#request.targetId()",
            metadata = "{reportCount: #result.reportCount(), targetDeleted: #result.targetDeleted(),"
                    + " violation: #result.violationRecorded(), confirmedReason: #request.confirmedReason(),"
                    + " note: #request.note()}")
    public Outcome hide(Long moderatorId, ReportDecisionRequest request) {
        List<Report> open = lockOpen(request);
        Optional<ReportTargets.Target> found = targets.find(request.targetType(), request.targetId());
        if (found.isEmpty()) {
            close(open, ReportStatus.RESOLVED, moderatorId);
            return new Outcome(open.size(), true, false);
        }
        ReportTargets.Target target = found.get();
        requireNotInvolved(target, moderatorId);
        ReportReason confirmed = confirmedReason(request, open);

        targets.hide(target);
        boolean violation =
                request.targetType() != ReportTargetType.PLACE || PLACE_VIOLATION_REASONS.contains(confirmed);
        if (violation) {
            violations.record(
                    target.author(),
                    ViolationSourceType.REPORT_CONFIRMED,
                    open.getFirst().getId(),
                    moderatorId);
        }
        close(open, ReportStatus.RESOLVED, moderatorId);
        return new Outcome(open.size(), false, violation);
    }

    /** Không vi phạm: đóng mọi báo cáo đang mở DISMISSED, không đụng tới đối tượng hay tác giả (UC29 3a). */
    @Transactional
    @AuditedAction(
            action = "REPORT_DISMISS",
            targetType = "#request.targetType()",
            targetId = "#request.targetId()",
            metadata = "{reportCount: #result.reportCount(), note: #request.note()}")
    public Outcome dismiss(Long moderatorId, ReportDecisionRequest request) {
        List<Report> open = lockOpen(request);
        Optional<ReportTargets.Target> target = targets.find(request.targetType(), request.targetId());
        target.ifPresent(t -> requireNotInvolved(t, moderatorId));
        close(open, ReportStatus.DISMISSED, moderatorId);
        return new Outcome(open.size(), target.isEmpty(), false);
    }

    private List<Report> lockOpen(ReportDecisionRequest request) {
        List<Report> open = reports.findOpenForUpdate(request.targetType(), request.targetId());
        if (open.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.REPORT_ALREADY_HANDLED,
                    "Các báo cáo về nội dung này đã được xử lý hoặc không tồn tại.");
        }
        return open;
    }

    private void requireNotInvolved(ReportTargets.Target target, Long moderatorId) {
        if (targets.isInvolved(target, moderatorId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.SELF_ACTION_FORBIDDEN,
                    "Không thể tự xử lý báo cáo về nội dung của chính mình.");
        }
    }

    /**
     * Ẩn địa điểm bắt buộc nêu lý do được xác nhận (O9 — nhóm có thể gộp nhiều lý do); lý do gửi lên phải có trong các
     * báo cáo đang mở, không được "nâng" WRONG_INFO thành SPAM để phạt người đề xuất.
     */
    private static ReportReason confirmedReason(ReportDecisionRequest request, List<Report> open) {
        ReportReason confirmed = request.confirmedReason();
        if (confirmed == null) {
            if (request.targetType() == ReportTargetType.PLACE) {
                throw ApiException.fieldError(
                        ErrorCode.VALIDATION_FAILED,
                        "confirmedReason",
                        "Cần chọn lý do được xác nhận khi ẩn địa điểm.");
            }
            return null;
        }
        if (open.stream().noneMatch(r -> r.getReason() == confirmed)) {
            throw ApiException.fieldError(
                    ErrorCode.VALIDATION_FAILED,
                    "confirmedReason",
                    "Lý do được xác nhận phải là một lý do trong các báo cáo đang chờ.");
        }
        return confirmed;
    }

    private void close(List<Report> open, ReportStatus status, Long moderatorId) {
        User moderator = users.getReferenceById(moderatorId);
        Instant now = clock.instant();
        for (Report report : open) {
            report.setStatus(status);
            report.setHandledBy(moderator);
            report.setHandledAt(now);
        }
    }

    private static ReportGroupResponse group(
            ReportTargetType type,
            Long id,
            ReportTargets.Target target,
            int count,
            Map<ReportReason, Integer> reasons,
            Instant firstReportedAt) {
        return new ReportGroupResponse(
                type,
                id,
                target == null ? null : target.preview(),
                target == null ? "DELETED" : target.status(),
                count,
                reasons,
                firstReportedAt);
    }

    private record GroupKey(ReportTargetType type, Long id) {}
}
