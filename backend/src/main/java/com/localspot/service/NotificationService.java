package com.localspot.service;

import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.NotificationResponse;
import com.localspot.entity.Notification;
import com.localspot.entity.NotificationType;
import com.localspot.entity.Place;
import com.localspot.entity.PlaceClaim;
import com.localspot.entity.Review;
import com.localspot.event.NotificationCreatedEvent;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.NotificationRepository;
import com.localspot.repository.UserRepository;
import com.localspot.security.Permissions;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thông báo trong ứng dụng (UC19, FR-27). Chốt 2026-10-16:
 *
 * <ul>
 *   <li><b>Ghi trong transaction của thao tác gây ra</b> ({@code Propagation.MANDATORY}): thao tác rollback thì không có
 *       thông báo "ma"; đẩy realtime chỉ sau commit ({@link NotificationCreatedEvent}).
 *   <li>{@code data} là <b>ảnh chụp</b> dữ liệu hiển thị lúc gửi (tên / slug địa điểm, lý do…) — danh sách thông báo
 *       không phải join lại nội dung đã có thể bị đổi tên / ẩn / xóa.
 *   <li>Chỉ các sự kiện trong UC ({@link NotificationType}); kiểm duyệt viên chỉ nhận yêu cầu sở hữu mới, mỗi người một
 *       dòng (UC23 bước 5) — hàng chờ khác xem ở màn kiểm duyệt.
 *   <li>Không tự gửi cho chính người gây ra thao tác.
 * </ul>
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notifications, UserRepository users, ApplicationEventPublisher events, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    // ─── Sự kiện (gọi từ service nghiệp vụ, trong transaction của chúng) ──────

    /** Kết quả duyệt địa điểm cho người đề xuất (UC27). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void placeModerated(Place place, boolean approved, String reason) {
        Map<String, Object> data = placeData(place);
        data.put("reason", reason);
        send(
                place.getCreatedBy().getId(),
                approved ? NotificationType.PLACE_APPROVED : NotificationType.PLACE_REJECTED,
                data);
    }

    /** Kết quả duyệt review cho tác giả (UC28 bước 4, 3a). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reviewModerated(Review review, boolean approved, String reason) {
        Map<String, Object> data = reviewData(review);
        data.put("reason", reason);
        send(
                review.getUser().getId(),
                approved ? NotificationType.REVIEW_PUBLISHED : NotificationType.REVIEW_REJECTED,
                data);
    }

    /** Review vừa được đăng (viết thẳng hoặc được duyệt) → chủ địa điểm, nếu có chủ (UC12 bước 8, UC28 bước 4). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reviewPublished(Review review) {
        Place place = review.getPlace();
        if (place.getOwner() == null
                || place.getOwner().getId().equals(review.getUser().getId())) {
            return;
        }
        Map<String, Object> data = reviewData(review);
        data.put("rating", review.getRating());
        data.put("authorName", review.getUser().getDisplayName());
        send(place.getOwner().getId(), NotificationType.NEW_REVIEW_ON_MY_PLACE, data);
    }

    /** Chủ phản hồi lần đầu → tác giả review (FR-33). Sửa phản hồi không gửi lại. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void ownerReplied(Review review, Long ownerId) {
        if (review.getUser().getId().equals(ownerId)) {
            return;
        }
        send(review.getUser().getId(), NotificationType.OWNER_REPLIED, reviewData(review));
    }

    /**
     * Báo cáo được xác nhận (UC29 bước 5): {@code affected} (tác giả / người đề xuất / chủ / người bị báo cáo) nhận
     * {@code REPORT_ACTION_TAKEN}, người báo cáo nhận {@code REPORT_RESOLVED}. {@code affected} rỗng khi đối tượng đã
     * bị xóa (3c) — chỉ người báo cáo được báo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reportActionTaken(
            String targetType,
            Long targetId,
            String preview,
            String confirmedReason,
            boolean violation,
            Collection<Long> affected,
            Collection<Long> reporters) {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("targetType", targetType);
        base.put("targetId", targetId);
        base.put("preview", preview);
        Map<String, Object> forAffected = new LinkedHashMap<>(base);
        forAffected.put("confirmedReason", confirmedReason);
        forAffected.put("violation", violation);
        for (Long userId : new LinkedHashSet<>(affected)) {
            send(userId, NotificationType.REPORT_ACTION_TAKEN, forAffected);
        }
        for (Long userId : new LinkedHashSet<>(reporters)) {
            send(userId, NotificationType.REPORT_RESOLVED, base);
        }
    }

    /** Yêu cầu sở hữu mới → mọi người có quyền duyệt, trừ chính người gửi (UC23 bước 5). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void claimSubmitted(PlaceClaim claim) {
        Map<String, Object> data = placeData(claim.getPlace());
        data.put("claimId", claim.getId());
        Long claimantId = claim.getUser().getId();
        for (Long moderatorId : users.findIdsWithPermission(Permissions.CLAIM_APPROVE)) {
            if (!moderatorId.equals(claimantId)) {
                send(moderatorId, NotificationType.CLAIM_SUBMITTED, data);
            }
        }
    }

    /** Kết quả yêu cầu sở hữu cho người yêu cầu (UC30), gồm yêu cầu bị tự từ chối. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void claimDecided(PlaceClaim claim, boolean approved, String reason, Long hiddenReviewId) {
        Map<String, Object> data = placeData(claim.getPlace());
        data.put("claimId", claim.getId());
        data.put("reason", reason);
        if (hiddenReviewId != null) {
            data.put("hiddenReviewId", hiddenReviewId);
        }
        send(
                claim.getUser().getId(),
                approved ? NotificationType.CLAIM_APPROVED : NotificationType.CLAIM_REJECTED,
                data);
    }

    private void send(Long recipientId, NotificationType type, Map<String, Object> data) {
        Notification saved =
                notifications.save(new Notification(users.getReferenceById(recipientId), type.name(), data));
        events.publishEvent(new NotificationCreatedEvent(recipientId, toResponse(saved)));
    }

    private static Map<String, Object> placeData(Place place) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("placeId", place.getId());
        data.put("placeName", place.getName());
        data.put("placeSlug", place.getSlug());
        return data;
    }

    private static Map<String, Object> reviewData(Review review) {
        Map<String, Object> data = placeData(review.getPlace());
        data.put("reviewId", review.getId());
        return data;
    }

    // ─── Đọc / đánh dấu (UC19) ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CursorPage<NotificationResponse> list(Long userId, boolean unreadOnly, String cursor, Integer limit) {
        int pageSize = KeysetCursor.limit(limit);
        List<Notification> rows =
                notifications.findPage(userId, unreadOnly, KeysetCursor.decode(cursor), Limit.of(pageSize + 1));
        return KeysetPages.byId(
                rows,
                pageSize,
                Notification::getId,
                page -> page.stream().map(NotificationService::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    /** Idempotent: đã đọc thì giữ thời điểm đọc cũ. Thông báo của người khác → 404 (không tiết lộ tồn tại). */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notifications
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ErrorCode.NOTIFICATION_NOT_FOUND, "Không tìm thấy thông báo."));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        notifications.markAllRead(userId, clock.instant());
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getData(), n.getReadAt(), n.getCreatedAt());
    }

    /** Người nhận khác nhau, bỏ null — tiện cho các nơi gom người liên quan. */
    static Set<Long> recipients(Long... ids) {
        Set<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                set.add(id);
            }
        }
        return set;
    }
}
