package com.localspot.service;

import com.localspot.config.TrustProperties;
import com.localspot.dto.request.ReviewCreateRequest;
import com.localspot.dto.request.ReviewUpdateRequest;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.PhotoResponse;
import com.localspot.dto.response.ReviewResponse;
import com.localspot.entity.Place;
import com.localspot.entity.PlaceStatus;
import com.localspot.entity.Review;
import com.localspot.entity.ReviewStatus;
import com.localspot.entity.User;
import com.localspot.event.ReviewChangedEvent;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.ReviewRepository;
import com.localspot.repository.UserRepository;
import com.localspot.service.ImageInspector.Inspected;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Review: viết (UC12, FR-17, FR-23), sửa / xóa của mình (UC13, FR-18), danh sách (FR-14). Duyệt review trong hàng chờ
 * (UC28) nằm ở mục "Trust score và hàng đợi duyệt review". Ảnh: {@link ReviewPhotoService}.
 *
 * <p><b>Viết review — thứ tự kiểm tra</b> (UC12, chốt 2026-10-08): email đã xác thực (403) → dữ liệu hợp lệ (422) → địa điểm APPROVED (404) →
 * không phải chủ địa điểm (409) → chưa có review, kể cả đã xóa (409, D2) → ≤ 5 review / 24 giờ (429) → trust ≥ 30 và
 * địa điểm chưa nhận ≥ 3 review từ cùng dải IP trong 24 giờ ⇒ PUBLISHED, ngược lại PENDING (dải IP: gắn cờ — U3).
 *
 * <p>Mọi thay đổi làm đổi tập review PUBLISHED của địa điểm phát {@link ReviewChangedEvent} để tính lại điểm Bayesian.
 */
@Service
public class ReviewService {

    /** "Ngày đã đến ≤ hôm nay" theo giờ Việt Nam — 23:30 ở Hà Nội đã là "hôm nay", dù UTC còn là hôm qua. */
    private static final ZoneId LOCAL_ZONE = PlaceService.VIEW_DATE_ZONE;

    /** U4. */
    static final int MIN_CONTENT_LENGTH = 20;

    private final ReviewRepository reviews;
    private final PlaceRepository places;
    private final UserRepository users;
    private final TrustScoreService trust;
    private final TrustProperties trustProperties;
    private final ReviewRateLimiter rateLimiter;
    private final IpAnomalyDetector ipAnomalies;
    private final ReviewViews views;
    private final ReviewPhotoService photos;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReviewService(
            ReviewRepository reviews,
            PlaceRepository places,
            UserRepository users,
            TrustScoreService trust,
            TrustProperties trustProperties,
            ReviewRateLimiter rateLimiter,
            IpAnomalyDetector ipAnomalies,
            ReviewViews views,
            ReviewPhotoService photos,
            ApplicationEventPublisher events,
            Clock clock) {
        this.reviews = reviews;
        this.places = places;
        this.users = users;
        this.trust = trust;
        this.trustProperties = trustProperties;
        this.rateLimiter = rateLimiter;
        this.ipAnomalies = ipAnomalies;
        this.views = views;
        this.photos = photos;
        this.events = events;
        this.clock = clock;
    }

    // ─── Viết (UC12) ─────────────────────────────────────────────────────────

    @Transactional
    public ReviewResponse create(
            Long userId, Long placeId, ReviewCreateRequest request, List<MultipartFile> photoFiles, ClientInfo client) {
        // Khóa dòng người dùng: các request đồng thời của cùng một người xếp hàng ở đây → đếm giới hạn đúng
        User author = users.findByIdForUpdate(userId).orElseThrow(ReviewService::userNotFound);
        if (!author.isEmailVerified()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, ErrorCode.EMAIL_NOT_VERIFIED, "Hãy xác thực email trước khi viết đánh giá.");
        }
        List<Inspected> images = photos.inspect(photoFiles); // ảnh hỏng / quá số → 422 cùng nhóm validate
        String content = checkContent(request.content());
        LocalDate visitedAt = checkVisitedAt(request.visitedAt());
        Place place = places.findById(placeId)
                .filter(p -> p.getStatus() == PlaceStatus.APPROVED)
                .orElseThrow(PlaceService::placeNotFound);
        if (place.getOwner() != null && place.getOwner().getId().equals(userId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.SELF_ACTION_FORBIDDEN,
                    "Chủ địa điểm không thể đánh giá địa điểm của mình.");
        }
        if (reviews.findIdIncludingDeleted(placeId, userId).isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.REVIEW_ALREADY_EXISTS,
                    "Bạn đã đánh giá địa điểm này. Hãy sửa đánh giá cũ thay vì viết mới.");
        }
        Instant now = clock.instant();
        rateLimiter.check(userId, now);

        String ipPrefix = IpPrefixes.of(client.ipAddress());
        boolean ipFlagged = ipAnomalies.isSuspicious(placeId, ipPrefix, now);
        boolean trusted = trust.trustScoreOf(author) >= trustProperties.publishThreshold();
        ReviewStatus status = trusted && !ipFlagged ? ReviewStatus.PUBLISHED : ReviewStatus.PENDING;

        Review review = new Review(
                place,
                author,
                request.rating(),
                content,
                visitedAt,
                status,
                Objects.requireNonNullElse(client.ipAddress(), "unknown"),
                ipPrefix);
        review.setIpFlagged(ipFlagged);
        reviews.saveAndFlush(review); // INSERT ngay: trùng UNIQUE (request chen nhau) → 409 tại đây, có createdAt
        photos.store(review, userId, images); // ảnh xử lý nền (UC12 bước 9)
        if (status == ReviewStatus.PUBLISHED) {
            events.publishEvent(new ReviewChangedEvent(review.getId(), placeId, null, status));
        }
        return views.of(review, userId);
    }

    // ─── Sửa / xóa của mình (UC13, FR-18) ────────────────────────────────────

    /**
     * Quyền tác giả đã kiểm ở {@code @PreAuthorize}. Luật theo trạng thái (chốt 2026-10-08):
     *
     * <ul>
     *   <li>HIDDEN (bị ẩn sau báo cáo) → 409 {@code REVIEW_LOCKED}: không để sửa lén nội dung bị báo cáo cho hiện lại.
     *   <li>REJECTED → gửi lại: luôn về PENDING (kiểm duyệt viên đã phản đối một lần), xóa lý do từ chối.
     *   <li>PUBLISHED / PENDING → tác giả trust &lt; 30 thì về PENDING (openapi), ngược lại giữ nguyên — review đang chờ
     *       (vd. bị gắn cờ IP) không tự được đăng chỉ vì sửa.
     * </ul>
     */
    @Transactional
    public ReviewResponse update(Long userId, Long reviewId, ReviewUpdateRequest request) {
        Review review = reviews.findWithAuthorById(reviewId).orElseThrow(ReviewService::reviewNotFound);
        if (request.version() != null && request.version() != review.getVersion()) {
            throw concurrentModification();
        }
        ReviewStatus before = review.getStatus();
        requireEditable(review);
        int ratingBefore = review.getRating();
        if (request.rating() != null) {
            review.setRating(request.rating());
        }
        if (request.content() != null) {
            review.setContent(checkContent(request.content()));
        }
        if (request.visitedAt() != null) {
            review.setVisitedAt(checkVisitedAt(request.visitedAt()));
        }

        if (before == ReviewStatus.REJECTED) {
            review.setStatus(ReviewStatus.PENDING);
            review.setRejectReason(null);
        } else if (trust.trustScoreOf(review.getUser()) < trustProperties.publishThreshold()) {
            review.setStatus(ReviewStatus.PENDING);
        }
        reviews.flush(); // UPDATE ngay → version mới trong response
        ReviewStatus after = review.getStatus();
        boolean publishedSetChanged =
                before != after && (before == ReviewStatus.PUBLISHED || after == ReviewStatus.PUBLISHED);
        boolean publishedRatingChanged = after == ReviewStatus.PUBLISHED && review.getRating() != ratingBefore;
        if (publishedSetChanged || publishedRatingChanged) {
            events.publishEvent(
                    new ReviewChangedEvent(reviewId, review.getPlace().getId(), before, after));
        }
        return views.of(review, userId);
    }

    /**
     * Thêm ảnh vào review của mình (chốt 2026-10-09): tổng ≤ {@code max-per-review}, không đổi trạng thái review. Khóa
     * dòng review để hai lần tải đồng thời lần lượt đếm giới hạn.
     */
    @Transactional
    public List<PhotoResponse> addPhotos(Long userId, Long reviewId, List<MultipartFile> photoFiles) {
        List<Inspected> images = photos.inspect(photoFiles);
        if (images.isEmpty()) {
            throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "photos", "Hãy chọn ít nhất một ảnh.");
        }
        Review review = reviews.findByIdForUpdate(reviewId).orElseThrow(ReviewService::reviewNotFound);
        requireEditable(review);
        return photos.store(review, userId, images);
    }

    @Transactional
    public void deletePhoto(Long reviewId, Long photoId) {
        Review review = reviews.findByIdForUpdate(reviewId).orElseThrow(ReviewService::reviewNotFound);
        requireEditable(review);
        photos.delete(reviewId, photoId);
    }

    /** Review bị ẩn sau báo cáo không sửa được — cả nội dung lẫn ảnh (chốt 2026-10-08). */
    private static void requireEditable(Review review) {
        if (review.getStatus() == ReviewStatus.HIDDEN) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.REVIEW_LOCKED,
                    "Đánh giá đã bị ẩn sau báo cáo vi phạm nên không sửa được.");
        }
    }

    /**
     * Xóa mềm (FR-18). Review đã xóa vẫn chiếm chỗ {@code UNIQUE(place_id, user_id)} → không viết lại được cho địa điểm
     * này (D2 — frontend cảnh báo trước khi xóa).
     */
    @Transactional
    public void delete(Long reviewId) {
        Review review = reviews.findById(reviewId).orElseThrow(ReviewService::reviewNotFound);
        ReviewStatus before = review.getStatus();
        review.setDeletedAt(clock.instant());
        if (before == ReviewStatus.PUBLISHED) {
            events.publishEvent(
                    new ReviewChangedEvent(reviewId, review.getPlace().getId(), before, null));
        }
    }

    // ─── Danh sách (FR-14) ──────────────────────────────────────────────────

    public enum Sort {
        NEWEST,
        HELPFUL
    }

    /** Review PUBLISHED của địa điểm đã duyệt; địa điểm chưa duyệt / không tồn tại → 404 như trang chi tiết. */
    @Transactional(readOnly = true)
    public CursorPage<ReviewResponse> listForPlace(
            Long placeId, Sort sort, Integer rating, String cursor, Integer limit, Long viewerId) {
        int pageSize = KeysetCursor.limit(limit);
        places.findById(placeId)
                .filter(p -> p.getStatus() == PlaceStatus.APPROVED)
                .orElseThrow(PlaceService::placeNotFound);
        if (sort == Sort.NEWEST) {
            List<Review> rows =
                    reviews.findPublishedNewest(placeId, rating, KeysetCursor.decode(cursor), Limit.of(pageSize + 1));
            return KeysetPages.byId(rows, pageSize, Review::getId, page -> views.of(page, viewerId));
        }
        ReviewListCursor.Helpful after = ReviewListCursor.decode(cursor);
        List<Review> rows = reviews.findPublishedMostHelpful(
                placeId,
                rating,
                after == null ? null : after.helpfulCount(),
                after == null ? null : after.id(),
                Limit.of(pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        List<Review> page = hasMore ? rows.subList(0, pageSize) : rows;
        String next = hasMore
                ? ReviewListCursor.encode(new ReviewListCursor.Helpful(
                        page.getLast().getHelpfulCount(), page.getLast().getId()))
                : null;
        return new CursorPage<>(views.of(page, viewerId), next);
    }

    /** {@code GET /me/reviews}: mọi trạng thái, kèm lý do từ chối. */
    @Transactional(readOnly = true)
    public CursorPage<ReviewResponse> writtenBy(Long userId, String cursor, Integer limit) {
        int pageSize = KeysetCursor.limit(limit);
        List<Review> rows = reviews.findWrittenBy(userId, KeysetCursor.decode(cursor), Limit.of(pageSize + 1));
        return KeysetPages.byId(rows, pageSize, Review::getId, page -> views.of(page, userId));
    }

    /** {@code GET /users/{id}/reviews}: chỉ PUBLISHED; người dùng không tồn tại → 404. */
    @Transactional(readOnly = true)
    public CursorPage<ReviewResponse> publishedBy(Long userId, String cursor, Integer limit, Long viewerId) {
        int pageSize = KeysetCursor.limit(limit);
        if (!users.existsById(userId)) {
            throw userNotFound();
        }
        List<Review> rows = reviews.findPublishedBy(userId, KeysetCursor.decode(cursor), Limit.of(pageSize + 1));
        return KeysetPages.byId(rows, pageSize, Review::getId, page -> views.of(page, viewerId));
    }

    // ─── Kiểm tra dữ liệu vào ───────────────────────────────────────────────

    /** U4: ≥ 20 ký tự sau khi bỏ khoảng trắng hai đầu — 20 dấu cách không phải một đánh giá. */
    private static String checkContent(String content) {
        String stripped = content.strip();
        if (stripped.length() < MIN_CONTENT_LENGTH) {
            throw ApiException.fieldError(
                    ErrorCode.VALIDATION_FAILED, "content", "Nội dung tối thiểu " + MIN_CONTENT_LENGTH + " ký tự.");
        }
        return stripped;
    }

    private LocalDate checkVisitedAt(LocalDate visitedAt) {
        if (visitedAt.isAfter(LocalDate.now(clock.withZone(LOCAL_ZONE)))) {
            throw ApiException.fieldError(
                    ErrorCode.VALIDATION_FAILED, "visitedAt", "Ngày đã đến không được ở tương lai.");
        }
        return visitedAt;
    }

    private static ApiException concurrentModification() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ErrorCode.CONCURRENT_MODIFICATION,
                "Đánh giá vừa được cập nhật ở nơi khác. Hãy tải lại rồi thử lại.");
    }

    static ApiException reviewNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.REVIEW_NOT_FOUND, "Không tìm thấy đánh giá.");
    }

    private static ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng.");
    }
}
