package com.localspot.service;

import com.localspot.audit.AuditedAction;
import com.localspot.config.ReviewProperties;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.ModerationReviewDetailResponse;
import com.localspot.dto.response.ModerationReviewResponse;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.dto.response.ReviewResponse;
import com.localspot.entity.Place;
import com.localspot.entity.Review;
import com.localspot.entity.ReviewStatus;
import com.localspot.entity.User;
import com.localspot.entity.ViolationSourceType;
import com.localspot.event.ReviewChangedEvent;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.ReviewRepository;
import com.localspot.repository.UserRepository;
import com.localspot.repository.UserViolationCount;
import com.localspot.repository.UserViolationRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
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
 * Duyệt review trong hàng chờ (UC28, FR-36) — các review cơ chế chống review ảo giữ lại (trust &lt; 30, cờ IP — FR-23).
 * Luật (chốt 2026-10-15, theo cùng khuôn với duyệt địa điểm 2026-10-04):
 *
 * <ul>
 *   <li>Chỉ quyết định trên review <b>PENDING</b> → PUBLISHED / REJECTED; review đã xử lý → 409
 *       {@code REVIEW_ALREADY_MODERATED}. Hàng chờ bỏ qua review trên địa điểm không còn APPROVED (đã bị ẩn) — duyệt
 *       cũng không ai thấy; quyết định trên chúng vẫn được phép.
 *   <li><b>Từ chối = một vi phạm cho tác giả</b> (UC28 3a, requirements §5.1) qua {@link ViolationService}, lý do bắt
 *       buộc — tác giả thấy lý do ở {@code /me/reviews}.
 *   <li>Không duyệt review của chính mình, hay review trên địa điểm mình sở hữu (409 {@code SELF_ACTION_FORBIDDEN}) —
 *       như không tự duyệt đề xuất / tự xử lý báo cáo.
 *   <li>Hai kiểm duyệt viên cùng lúc: {@code @Version} của {@link Review} → người sau 409
 *       {@code CONCURRENT_MODIFICATION}, vi phạm của người sau rollback cùng; client gửi {@code version} lệch → 409 ngay.
 * </ul>
 *
 * Duyệt phát {@link ReviewChangedEvent} (tập PUBLISHED đổi → Bayesian, điểm đóng góp, cache trang chủ tự cập nhật); từ
 * chối thì không — review PENDING chưa từng được tính. Mỗi quyết định ghi {@code activity_log} (FR-42). Tác giả nhận
 * kết quả, chủ địa điểm nhận review mới khi được duyệt (UC28 bước 4, 3a).
 */
@Service
public class ReviewModerationService {

    /** Số review cùng dải IP tối đa trên màn duyệt — đủ để thấy một "trang trại" review, không tải vô hạn. */
    private static final int SAME_SUBNET_LIMIT = 20;

    private final ReviewRepository reviews;
    private final UserRepository users;
    private final UserViolationRepository violationCounts;
    private final ViolationService violations;
    private final TrustScoreService trust;
    private final ReviewViews views;
    private final PlaceSummaries summaries;
    private final ReviewProperties reviewProperties;
    private final ApplicationEventPublisher events;
    private final NotificationService notifications;
    private final Clock clock;

    public ReviewModerationService(
            ReviewRepository reviews,
            UserRepository users,
            UserViolationRepository violationCounts,
            ViolationService violations,
            TrustScoreService trust,
            ReviewViews views,
            PlaceSummaries summaries,
            ReviewProperties reviewProperties,
            ApplicationEventPublisher events,
            NotificationService notifications,
            Clock clock) {
        this.reviews = reviews;
        this.users = users;
        this.violationCounts = violationCounts;
        this.violations = violations;
        this.trust = trust;
        this.views = views;
        this.summaries = summaries;
        this.reviewProperties = reviewProperties;
        this.events = events;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ─── Hàng chờ (UC28 bước 1) ──────────────────────────────────────────────

    /** FIFO theo id (= thứ tự viết); {@code ipFlagged} lọc theo cờ nghi ngờ IP, {@code null} = tất cả. */
    @Transactional(readOnly = true)
    public CursorPage<ModerationReviewResponse> queue(
            Boolean ipFlagged, String cursor, Integer limit, Long moderatorId) {
        int pageSize = KeysetCursor.limit(limit);
        List<Review> rows = reviews.findModerationQueue(ipFlagged, KeysetCursor.decode(cursor), Limit.of(pageSize + 1));
        return KeysetPages.byId(rows, pageSize, Review::getId, page -> toItems(page, moderatorId));
    }

    /** Mỗi loại dữ liệu kèm theo một truy vấn cho cả trang — không truy vấn từng review (N+1). */
    private List<ModerationReviewResponse> toItems(List<Review> page, Long moderatorId) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<ReviewResponse> cards = views.of(page, moderatorId);
        Map<Long, PlaceSummaryResponse> placeCards = placeCardsOf(page);
        List<User> authors = page.stream().map(Review::getUser).distinct().toList();
        Map<Long, Integer> trustScores = trust.trustScoresOf(authors);
        Map<Long, Long> activeViolations =
                violationCounts
                        .countActiveByUserIds(authors.stream().map(User::getId).toList(), clock.instant())
                        .stream()
                        .collect(Collectors.toMap(UserViolationCount::userId, UserViolationCount::count));
        Map<Long, Long> sameSubnet = new HashMap<>();
        for (Object[] row :
                reviews.countSameSubnetNearby(page.stream().map(Review::getId).toList(), ipWindowSeconds())) {
            sameSubnet.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        List<ModerationReviewResponse> items = new ArrayList<>(page.size());
        for (int i = 0; i < page.size(); i++) {
            Review review = page.get(i);
            Long authorId = review.getUser().getId();
            items.add(new ModerationReviewResponse(
                    cards.get(i),
                    placeCards.get(review.getPlace().getId()),
                    trustScores.get(authorId),
                    activeViolations.getOrDefault(authorId, 0L),
                    review.isIpFlagged(),
                    sameSubnet.getOrDefault(review.getId(), 0L)));
        }
        return items;
    }

    /** Một trang có thể có nhiều review cùng địa điểm — dựng thẻ mỗi địa điểm một lần. */
    private Map<Long, PlaceSummaryResponse> placeCardsOf(List<Review> page) {
        List<Place> distinctPlaces = page.stream()
                .map(Review::getPlace)
                .collect(Collectors.toMap(Place::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values()
                .stream()
                .toList();
        return summaries.of(distinctPlaces).stream()
                .collect(Collectors.toMap(PlaceSummaryResponse::id, Function.identity()));
    }

    // ─── Màn duyệt một review (UC28 bước 2) ──────────────────────────────────

    /**
     * Mọi trạng thái (review vừa bị người khác xử lý vẫn mở được, hiện kết quả); review đã xóa → 404. Review cùng dải IP
     * trong cửa sổ của luật U3, hai phía thời điểm viết.
     */
    @Transactional(readOnly = true)
    public ModerationReviewDetailResponse detail(Long reviewId, Long moderatorId) {
        Review review = reviews.findForModerationById(reviewId).orElseThrow(ReviewService::reviewNotFound);
        ModerationReviewResponse item = toItems(List.of(review), moderatorId).getFirst();

        Map<ReviewStatus, Long> counts = new EnumMap<>(ReviewStatus.class);
        for (ReviewStatus status : ReviewStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : reviews.countByStatusOfAuthor(review.getUser().getId())) {
            counts.put((ReviewStatus) row[0], ((Number) row[1]).longValue());
        }

        Duration window = reviewProperties.ipWindow();
        List<Review> sameSubnet = reviews.findSameSubnetBetween(
                review.getPlace().getId(),
                review.getIpPrefix(),
                reviewId,
                review.getCreatedAt().minus(window),
                review.getCreatedAt().plus(window),
                Limit.of(SAME_SUBNET_LIMIT));
        return new ModerationReviewDetailResponse(
                item, review.getUser().getCreatedAt(), counts, views.of(sameSubnet, moderatorId));
    }

    private long ipWindowSeconds() {
        return reviewProperties.ipWindow().toSeconds();
    }

    // ─── Quyết định (UC28 bước 3–5, 3a, 3b) ──────────────────────────────────

    @Transactional
    @AuditedAction(action = "REVIEW_APPROVE", targetType = "REVIEW", targetId = "#reviewId")
    public void approve(Long moderatorId, Long reviewId, Integer version) {
        Review review = pendingReviewFor(moderatorId, reviewId, version);
        review.setStatus(ReviewStatus.PUBLISHED);
        markModerated(review, moderatorId);
        events.publishEvent(new ReviewChangedEvent(
                reviewId, review.getPlace().getId(), ReviewStatus.PENDING, ReviewStatus.PUBLISHED));
        notifications.reviewModerated(review, true, null);
        notifications.reviewPublished(review);
    }

    @Transactional
    @AuditedAction(
            action = "REVIEW_REJECT",
            targetType = "REVIEW",
            targetId = "#reviewId",
            metadata = "{reason: #reason.strip()}")
    public void reject(Long moderatorId, Long reviewId, String reason, Integer version) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "reason", "Cần nêu lý do khi từ chối.");
        }
        Review review = pendingReviewFor(moderatorId, reviewId, version);
        review.setStatus(ReviewStatus.REJECTED);
        review.setRejectReason(reason.strip());
        markModerated(review, moderatorId);
        violations.record(review.getUser(), ViolationSourceType.REVIEW_REJECTED, reviewId, moderatorId);
        notifications.reviewModerated(review, false, reason.strip());
    }

    private Review pendingReviewFor(Long moderatorId, Long reviewId, Integer version) {
        Review review = reviews.findForModerationById(reviewId).orElseThrow(ReviewService::reviewNotFound);
        if (review.getStatus() != ReviewStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.REVIEW_ALREADY_MODERATED,
                    "Đánh giá này đã được xử lý (" + review.getStatus().name() + ").");
        }
        if (version != null && version != review.getVersion()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "Đánh giá vừa được cập nhật ở nơi khác. Hãy tải lại rồi thử lại.");
        }
        User owner = review.getPlace().getOwner();
        if (review.getUser().getId().equals(moderatorId)
                || (owner != null && owner.getId().equals(moderatorId))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.SELF_ACTION_FORBIDDEN,
                    "Không thể tự duyệt đánh giá của mình hay đánh giá về địa điểm mình sở hữu.");
        }
        return review;
    }

    private void markModerated(Review review, Long moderatorId) {
        review.setModeratedBy(users.getReferenceById(moderatorId));
        review.setModeratedAt(clock.instant());
    }
}
