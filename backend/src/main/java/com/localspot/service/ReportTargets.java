package com.localspot.service;

import com.localspot.entity.Comment;
import com.localspot.entity.CommentStatus;
import com.localspot.entity.Place;
import com.localspot.entity.PlaceStatus;
import com.localspot.entity.ReportTargetType;
import com.localspot.entity.Review;
import com.localspot.entity.ReviewStatus;
import com.localspot.entity.User;
import com.localspot.event.ContributionChangedEvent;
import com.localspot.event.PlaceIndexChangedEvent;
import com.localspot.event.ReviewChangedEvent;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.CommentRepository;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.ReviewRepository;
import com.localspot.repository.UserRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Quan hệ đa hình của {@code reports} (không có FK — toàn vẹn kiểm ở đây): tìm, mô tả và ẩn đối tượng bị báo cáo theo
 * {@link ReportTargetType}. Gom bốn nhánh {@code switch} vào một chỗ để {@link ReportService} và
 * {@link ReportModerationService} không lặp lại.
 *
 * <p><b>"Tác giả"</b> = người chịu trách nhiệm nội dung: người đề xuất địa điểm ({@code places.created_by} — tên, vị trí,
 * danh mục do họ nhập, O9), tác giả review / bình luận, chính người bị báo cáo.
 */
@Component
class ReportTargets {

    /** Đối tượng đã nạp. {@code status} là tên trạng thái để hiển thị; người dùng: {@code ACTIVE} / {@code LOCKED}. */
    record Target(ReportTargetType type, Long id, String preview, String status, User author) {}

    private final PlaceRepository places;
    private final ReviewRepository reviews;
    private final CommentRepository comments;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ReportTargets(
            PlaceRepository places,
            ReviewRepository reviews,
            CommentRepository comments,
            UserRepository users,
            ApplicationEventPublisher events,
            Clock clock) {
        this.places = places;
        this.reviews = reviews;
        this.comments = comments;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    // ─── Người báo cáo (UC17) ───────────────────────────────────────────────

    /**
     * Chỉ báo cáo được thứ người báo cáo đang nhìn thấy công khai: địa điểm APPROVED, review PUBLISHED trên địa điểm
     * APPROVED, bình luận VISIBLE trên review như vậy, người dùng chưa xóa. Không thấy → 404 mã theo loại (như các
     * endpoint đọc — không tiết lộ nội dung chưa duyệt / đã ẩn có tồn tại).
     *
     * @return tác giả đối tượng — để chặn tự báo cáo nội dung của mình
     */
    User requireVisible(ReportTargetType type, Long id) {
        return switch (type) {
            case PLACE ->
                places.findById(id)
                        .filter(p -> p.getStatus() == PlaceStatus.APPROVED)
                        .map(Place::getCreatedBy)
                        .orElseThrow(PlaceService::placeNotFound);
            case REVIEW -> reviews.findVisibleById(id).map(Review::getUser).orElseThrow(ReviewService::reviewNotFound);
            case COMMENT ->
                comments.findById(id)
                        .filter(c -> c.getStatus() == CommentStatus.VISIBLE)
                        .filter(c ->
                                reviews.findVisibleById(c.getReview().getId()).isPresent())
                        .map(Comment::getUser)
                        .orElseThrow(ReportTargets::commentNotFound);
            case USER -> users.findById(id).orElseThrow(ReportTargets::userNotFound);
        };
    }

    // ─── Kiểm duyệt viên (UC29) ─────────────────────────────────────────────

    /** Nạp theo lô cho một trang hàng đợi — mỗi loại một truy vấn. Đối tượng đã xóa mềm không có trong kết quả. */
    Map<Long, Target> load(ReportTargetType type, Collection<Long> ids) {
        Map<Long, Target> byId = HashMap.newHashMap(ids.size());
        if (ids.isEmpty()) {
            return byId;
        }
        switch (type) {
            case PLACE -> places.findAllById(ids).forEach(p -> byId.put(p.getId(), of(p)));
            case REVIEW -> reviews.findWithPlaceAndAuthorByIdIn(ids).forEach(r -> byId.put(r.getId(), of(r)));
            case COMMENT -> comments.findWithAuthorByIdIn(ids).forEach(c -> byId.put(c.getId(), of(c)));
            case USER -> users.findAllById(ids).forEach(u -> byId.put(u.getId(), of(u)));
        }
        return byId;
    }

    Optional<Target> find(ReportTargetType type, Long id) {
        return Optional.ofNullable(load(type, List.of(id)).get(id));
    }

    /**
     * Người được coi là "chính mình" với kiểm duyệt viên — không tự xử lý báo cáo về nội dung của mình (như không tự
     * duyệt đề xuất, 2026-10-04). Với địa điểm gồm cả chủ: chủ có lợi ích trực tiếp dù không phải người đề xuất.
     */
    boolean isInvolved(Target target, Long moderatorId) {
        if (target.author().getId().equals(moderatorId)) {
            return true;
        }
        if (target.type() == ReportTargetType.PLACE) {
            User owner = places.getReferenceById(target.id()).getOwner();
            return owner != null && owner.getId().equals(moderatorId);
        }
        return false;
    }

    /**
     * Ẩn đối tượng — đổi trạng thái, <b>không xóa dữ liệu</b> (UC29 quy tắc, CLAUDE.md §4), rồi phát các sự kiện để
     * dữ liệu dẫn xuất tự cập nhật sau commit. Người dùng không có trạng thái ẩn (chốt 2026-10-14) — không làm gì.
     */
    void hide(Target target) {
        switch (target.type()) {
            case PLACE -> hidePlace(target.id());
            case REVIEW -> hideReview(target.id());
            case COMMENT -> comments.getReferenceById(target.id()).setStatus(CommentStatus.HIDDEN);
            case USER -> {
                // Khóa tài khoản là thao tác riêng của quản trị viên (UC29 3b, POST /admin/users/{id}/lock)
            }
        }
    }

    /**
     * Ẩn địa điểm: rời khỏi tìm kiếm + trang chủ ({@link PlaceIndexChangedEvent} — consumer đọc trạng thái hiện tại);
     * điểm đóng góp tính từ nội dung đang hiện (O3) nên người đề xuất (+15) và tác giả các review trên đó bị tính lại.
     * Check-in của người khác trên địa điểm và hằng số {@code C} của Bayesian được job đêm đối soát (D4).
     */
    private void hidePlace(Long placeId) {
        Place place = places.getReferenceById(placeId);
        place.setStatus(PlaceStatus.HIDDEN);
        events.publishEvent(PlaceIndexChangedEvent.of(placeId));
        events.publishEvent(new ContributionChangedEvent(place.getCreatedBy().getId()));
        for (Long authorId : reviews.findPublishedAuthorIdsOfPlace(placeId)) {
            events.publishEvent(new ContributionChangedEvent(authorId));
        }
    }

    /** Ẩn review: rời tập PUBLISHED → tính lại Bayesian, điểm đóng góp, cache trang chủ (đều nghe {@link ReviewChangedEvent}). */
    private void hideReview(Long reviewId) {
        Review review = reviews.getReferenceById(reviewId);
        ReviewStatus before = review.getStatus();
        review.setStatus(ReviewStatus.HIDDEN);
        if (before == ReviewStatus.PUBLISHED) {
            events.publishEvent(
                    new ReviewChangedEvent(reviewId, review.getPlace().getId(), before, ReviewStatus.HIDDEN));
        }
    }

    private Target of(Place place) {
        return new Target(
                ReportTargetType.PLACE,
                place.getId(),
                place.getName(),
                place.getStatus().name(),
                place.getCreatedBy());
    }

    private Target of(Review review) {
        String preview = review.getPlace().getName() + " — " + HomeService.excerpt(review.getContent());
        return new Target(
                ReportTargetType.REVIEW,
                review.getId(),
                preview,
                review.getStatus().name(),
                review.getUser());
    }

    private Target of(Comment comment) {
        return new Target(
                ReportTargetType.COMMENT,
                comment.getId(),
                HomeService.excerpt(comment.getContent()),
                comment.getStatus().name(),
                comment.getUser());
    }

    private Target of(User user) {
        String status = user.isLockedAt(clock.instant()) ? "LOCKED" : "ACTIVE";
        return new Target(ReportTargetType.USER, user.getId(), user.getDisplayName(), status, user);
    }

    private static ApiException commentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.COMMENT_NOT_FOUND, "Không tìm thấy bình luận.");
    }

    private static ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng.");
    }
}
