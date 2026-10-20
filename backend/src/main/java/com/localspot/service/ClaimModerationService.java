package com.localspot.service;

import com.localspot.audit.AuditedAction;
import com.localspot.dto.response.ClaimResponse;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.EvidenceLinkResponse;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.entity.ClaimStatus;
import com.localspot.entity.Place;
import com.localspot.entity.PlaceClaim;
import com.localspot.entity.Review;
import com.localspot.entity.ReviewStatus;
import com.localspot.entity.Role;
import com.localspot.entity.User;
import com.localspot.event.ReviewChangedEvent;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.mapper.ReviewMapper;
import com.localspot.repository.PlaceClaimRepository;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.ReviewRepository;
import com.localspot.repository.RoleRepository;
import com.localspot.repository.UserRepository;
import com.localspot.storage.ObjectStorage;
import com.localspot.storage.StorageException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm duyệt viên duyệt yêu cầu sở hữu (UC30, FR-40). Luật (chốt 2026-10-16):
 *
 * <ul>
 *   <li>Chỉ quyết định trên yêu cầu <b>PENDING</b> → 409 {@code CLAIM_ALREADY_MODERATED}; không tự duyệt yêu cầu của
 *       mình (409 {@code SELF_ACTION_FORBIDDEN}); {@code version} lệch → 409 {@code CONCURRENT_MODIFICATION}.
 *   <li><b>Duyệt</b> khóa dòng địa điểm trước rồi mới đọc yêu cầu: hai kiểm duyệt viên duyệt hai yêu cầu khác nhau của
 *       cùng địa điểm thì người sau chờ, đọc lại thấy yêu cầu của mình đã bị tự từ chối → 409 — không bao giờ có hai chủ
 *       (U6). Duyệt = gán {@code places.owner_id}, thêm role OWNER nếu chưa có (quyền nạp lại từ CSDL mỗi request — có
 *       hiệu lực ngay), tự từ chối các yêu cầu đang chờ khác của địa điểm.
 *   <li><b>Review sẵn có của chủ mới</b> trên địa điểm bị ẩn (HIDDEN, không ghi vi phạm — supervisor chốt 2026-10-16):
 *       chủ không được chấm điểm địa điểm của mình, kể cả review viết từ trước khi là chủ. Ẩn ở mọi trạng thái chưa xóa
 *       (PENDING / REJECTED có thể được duyệt / gửi lại sau này); review đang PUBLISHED thì tính lại điểm Bayesian.
 *   <li><b>Từ chối</b> cần lý do, <b>không</b> ghi vi phạm — requirements §5.1 chỉ tính review bị từ chối và báo cáo được
 *       xác nhận; người dùng gửi lại được.
 * </ul>
 *
 * Mỗi quyết định ghi {@code activity_log} (FR-42) và thông báo kết quả cho người yêu cầu — kể cả các yêu cầu bị tự từ
 * chối.
 */
@Service
public class ClaimModerationService {

    /** openapi: link tạm 5 phút — đủ để mở xem, hết hạn trước khi kịp bị chia sẻ rộng. */
    static final Duration EVIDENCE_LINK_TTL = Duration.ofMinutes(5);

    static final String AUTO_REJECT_REASON = "Địa điểm đã được xác nhận cho một chủ khác.";
    static final String OWNER_REVIEW_HIDDEN_REASON = "Đánh giá được ẩn vì bạn đã trở thành chủ địa điểm này.";

    /** Kết quả duyệt — nguồn metadata của {@code activity_log}. */
    public record Approval(Long placeId, Long claimantId, int autoRejectedCount, Long hiddenReviewId) {}

    private final PlaceClaimRepository claims;
    private final PlaceRepository places;
    private final UserRepository users;
    private final RoleRepository roles;
    private final ReviewRepository reviews;
    private final PlaceSummaries summaries;
    private final ReviewMapper userMapper;
    private final ObjectStorage storage;
    private final ApplicationEventPublisher events;
    private final NotificationService notifications;
    private final Clock clock;

    public ClaimModerationService(
            PlaceClaimRepository claims,
            PlaceRepository places,
            UserRepository users,
            RoleRepository roles,
            ReviewRepository reviews,
            PlaceSummaries summaries,
            ReviewMapper userMapper,
            ObjectStorage storage,
            ApplicationEventPublisher events,
            NotificationService notifications,
            Clock clock) {
        this.claims = claims;
        this.places = places;
        this.users = users;
        this.roles = roles;
        this.reviews = reviews;
        this.summaries = summaries;
        this.userMapper = userMapper;
        this.storage = storage;
        this.events = events;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ─── Hàng chờ và màn duyệt ───────────────────────────────────────────────

    /** PENDING, FIFO theo id (= thứ tự gửi). */
    @Transactional(readOnly = true)
    public CursorPage<ClaimResponse> queue(String cursor, Integer limit) {
        int pageSize = KeysetCursor.limit(limit);
        List<PlaceClaim> rows = claims.findPendingOldestFirst(KeysetCursor.decode(cursor), Limit.of(pageSize + 1));
        return KeysetPages.byId(rows, pageSize, PlaceClaim::getId, this::toResponses);
    }

    /** Mọi trạng thái — yêu cầu vừa được người khác xử lý vẫn mở được để hiện kết quả. */
    @Transactional(readOnly = true)
    public ClaimResponse detail(Long claimId) {
        PlaceClaim claim = claims.findDetailById(claimId).orElseThrow(ClaimModerationService::claimNotFound);
        return toResponses(List.of(claim)).getFirst();
    }

    /** Thẻ địa điểm và số yêu cầu đang chờ theo địa điểm nạp một lần cho cả trang. */
    private List<ClaimResponse> toResponses(List<PlaceClaim> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<Place> distinctPlaces = page.stream()
                .map(PlaceClaim::getPlace)
                .collect(Collectors.toMap(Place::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values()
                .stream()
                .toList();
        Map<Long, PlaceSummaryResponse> cards = summaries.of(distinctPlaces).stream()
                .collect(Collectors.toMap(PlaceSummaryResponse::id, Function.identity()));
        Map<Long, Long> pendingByPlace = new HashMap<>();
        for (Object[] row : claims.countPendingByPlaceIds(cards.keySet())) {
            pendingByPlace.put((Long) row[0], ((Number) row[1]).longValue());
        }
        List<ClaimResponse> items = new ArrayList<>(page.size());
        for (PlaceClaim claim : page) {
            Long placeId = claim.getPlace().getId();
            long pending = pendingByPlace.getOrDefault(placeId, 0L);
            long others = claim.getStatus() == ClaimStatus.PENDING ? pending - 1 : pending;
            items.add(new ClaimResponse(
                    claim.getId(),
                    cards.get(placeId),
                    userMapper.toUserSummary(claim.getUser()),
                    claim.getContactPhone(),
                    claim.getEvidenceNote(),
                    claim.getEvidenceKeys().size(),
                    claim.getStatus(),
                    claim.getRejectReason(),
                    others,
                    claim.getCreatedAt(),
                    claim.getVersion()));
        }
        return items;
    }

    /**
     * Link tạm tới file minh chứng thứ {@code index} (0-based); không có → 404. Ký cục bộ, không gọi kho; mỗi lần gọi ký
     * link mới. {@code expiresAt} tính trước khi ký nên không muộn hơn hạn thật của link.
     */
    @Transactional(readOnly = true)
    public EvidenceLinkResponse evidenceLink(Long claimId, int index) {
        PlaceClaim claim = claims.findById(claimId).orElseThrow(ClaimModerationService::claimNotFound);
        List<String> keys = claim.getEvidenceKeys();
        if (index < 0 || index >= keys.size()) {
            throw claimNotFound();
        }
        try {
            Instant expiresAt = clock.instant().plus(EVIDENCE_LINK_TTL);
            return new EvidenceLinkResponse(storage.presignedGetUrl(keys.get(index), EVIDENCE_LINK_TTL), expiresAt);
        } catch (StorageException e) {
            throw PhotoFiles.storageUnavailable();
        }
    }

    // ─── Quyết định ──────────────────────────────────────────────────────────

    @Transactional
    @AuditedAction(
            action = "CLAIM_APPROVE",
            targetType = "CLAIM",
            targetId = "#claimId",
            metadata = "{placeId: #result.placeId(), claimantId: #result.claimantId(),"
                    + " autoRejected: #result.autoRejectedCount(), hiddenReviewId: #result.hiddenReviewId()}")
    public Approval approve(Long moderatorId, Long claimId, Integer version) {
        Long placeId = claims.findPlaceIdById(claimId).orElseThrow(ClaimModerationService::claimNotFound);
        Place place = places.findByIdForUpdate(placeId).orElseThrow(PlaceService::placeNotFound);
        PlaceClaim claim = pendingClaimFor(moderatorId, claimId, version);
        if (place.getOwner() != null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PLACE_ALREADY_OWNED, "Địa điểm này đã có chủ.");
        }
        User owner = claim.getUser();
        Instant now = clock.instant();

        place.setOwner(owner);
        if (owner.getRoles().stream().noneMatch(role -> Role.OWNER.equals(role.getName()))) {
            owner.getRoles()
                    .add(roles.findByName(Role.OWNER)
                            .orElseThrow(() -> new IllegalStateException("Thiếu role OWNER trong CSDL")));
        }
        markReviewed(claim, ClaimStatus.APPROVED, null, moderatorId, now);

        List<PlaceClaim> others = claims.findOtherPending(placeId, claimId);
        others.forEach(other -> markReviewed(other, ClaimStatus.REJECTED, AUTO_REJECT_REASON, moderatorId, now));

        Long hiddenReviewId = hideOwnerReview(placeId, owner.getId());
        notifications.claimDecided(claim, true, null, hiddenReviewId);
        others.forEach(other -> notifications.claimDecided(other, false, AUTO_REJECT_REASON, null));
        return new Approval(placeId, owner.getId(), others.size(), hiddenReviewId);
    }

    /** Lý do bắt buộc — người yêu cầu nhận lý do qua thông báo. */
    @Transactional
    @AuditedAction(
            action = "CLAIM_REJECT",
            targetType = "CLAIM",
            targetId = "#claimId",
            metadata = "{reason: #reason.strip()}")
    public void reject(Long moderatorId, Long claimId, String reason, Integer version) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "reason", "Cần nêu lý do khi từ chối.");
        }
        PlaceClaim claim = pendingClaimFor(moderatorId, claimId, version);
        markReviewed(claim, ClaimStatus.REJECTED, reason.strip(), moderatorId, clock.instant());
        notifications.claimDecided(claim, false, reason.strip(), null);
    }

    /** Review (chưa xóa) của chủ mới trên địa điểm → HIDDEN; trả id review đã ẩn, {@code null} nếu không có. */
    private Long hideOwnerReview(Long placeId, Long ownerId) {
        Review review = reviews.findByPlaceIdAndUserId(placeId, ownerId).orElse(null);
        if (review == null || review.getStatus() == ReviewStatus.HIDDEN) {
            return null;
        }
        ReviewStatus before = review.getStatus();
        review.setStatus(ReviewStatus.HIDDEN);
        review.setRejectReason(OWNER_REVIEW_HIDDEN_REASON);
        if (before == ReviewStatus.PUBLISHED) {
            events.publishEvent(new ReviewChangedEvent(review.getId(), placeId, before, ReviewStatus.HIDDEN));
        }
        return review.getId();
    }

    private PlaceClaim pendingClaimFor(Long moderatorId, Long claimId, Integer version) {
        PlaceClaim claim = claims.findDetailById(claimId).orElseThrow(ClaimModerationService::claimNotFound);
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.CLAIM_ALREADY_MODERATED,
                    "Yêu cầu này đã được xử lý (" + claim.getStatus().name() + ").");
        }
        if (version != null && version != claim.getVersion()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "Yêu cầu vừa được cập nhật ở nơi khác. Hãy tải lại rồi thử lại.");
        }
        if (claim.getUser().getId().equals(moderatorId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.SELF_ACTION_FORBIDDEN,
                    "Không thể tự duyệt yêu cầu sở hữu của chính mình.");
        }
        return claim;
    }

    private void markReviewed(PlaceClaim claim, ClaimStatus status, String reason, Long moderatorId, Instant at) {
        claim.setStatus(status);
        claim.setRejectReason(reason);
        claim.setReviewedBy(users.getReferenceById(moderatorId));
        claim.setReviewedAt(at);
    }

    static ApiException claimNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.CLAIM_NOT_FOUND, "Không tìm thấy yêu cầu sở hữu.");
    }
}
