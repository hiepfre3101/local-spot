package com.localspot.service;

import com.localspot.audit.AuditedAction;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.ModerationPlaceDetailResponse;
import com.localspot.dto.response.ModerationPlaceResponse;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.entity.Place;
import com.localspot.entity.PlaceStatus;
import com.localspot.event.ContributionChangedEvent;
import com.localspot.event.PlaceIndexChangedEvent;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.mapper.PlaceMapper;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.UserRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Duyệt địa điểm (UC27, FR-35). Luật chốt 2026-10-04:
 *
 * <ul>
 *   <li>Chỉ quyết định trên địa điểm <b>PENDING</b> → APPROVED / REJECTED; địa điểm đã xử lý → 409
 *       {@code PLACE_ALREADY_MODERATED} (không đảo quyết định ở đây: APPROVED → REJECTED sẽ đụng review / rating đã có).
 *   <li>Kiểm duyệt viên <b>không duyệt đề xuất của chính mình</b> (409 {@code SELF_ACTION_FORBIDDEN}) — tránh xung đột
 *       lợi ích; người khác trong đội duyệt.
 *   <li>Hai kiểm duyệt viên xử lý cùng lúc: {@code @Version} của {@link Place} để người sau nhận 409
 *       {@code CONCURRENT_MODIFICATION}; client gửi kèm {@code version} đang thấy thì lệch cũng 409 ngay.
 * </ul>
 *
 * Mỗi quyết định ghi {@code activity_log} qua {@link AuditedAction} (FR-42). Người đề xuất nhận thông báo kết quả (UC11 hậu
 * điều kiện). Duyệt thì đồng bộ index tìm kiếm sau commit (FR-35, {@link PlaceIndexChangedEvent});
 * từ chối thì không — địa điểm PENDING chưa từng có trong index.
 */
@Service
public class PlaceModerationService {

    private final PlaceRepository places;
    private final UserRepository users;
    private final PlaceSummaries summaries;
    private final NearbyPlaceService nearby;
    private final PlaceService placeService;
    private final PlaceMapper mapper;
    private final ApplicationEventPublisher events;
    private final NotificationService notifications;
    private final Clock clock;

    public PlaceModerationService(
            PlaceRepository places,
            UserRepository users,
            PlaceSummaries summaries,
            NearbyPlaceService nearby,
            PlaceService placeService,
            PlaceMapper mapper,
            ApplicationEventPublisher events,
            NotificationService notifications,
            Clock clock) {
        this.places = places;
        this.users = users;
        this.summaries = summaries;
        this.nearby = nearby;
        this.placeService = placeService;
        this.mapper = mapper;
        this.events = events;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Hàng chờ theo trạng thái ({@code null} = PENDING). PENDING xếp FIFO — cũ nhất trước, như hàng chờ gộp; trạng thái
     * đã xử lý là lịch sử nên mới nhất trước.
     *
     * <p>Mục PENDING kèm {@code possibleDuplicates} (U7 phía kiểm duyệt — 2026-10-07): hai người dùng có thể đề xuất cùng
     * một quán mà không thấy đề xuất chờ của nhau, nên kiểm duyệt viên là nơi bắt trùng giữa người dùng. Mỗi mục một truy
     * vấn không gian trên spatial index (trang ≤ 50 mục) — chấp nhận ở quy mô hàng chờ, đổi lại không gộp SQL phức tạp.
     */
    @Transactional(readOnly = true)
    public CursorPage<ModerationPlaceResponse> queue(PlaceStatus status, String cursor, Integer limit) {
        PlaceStatus effective = status == null ? PlaceStatus.PENDING : status;
        int pageSize = KeysetCursor.limit(limit);
        Long afterId = KeysetCursor.decode(cursor);
        List<Place> rows = effective == PlaceStatus.PENDING
                ? places.findByStatusOldestFirst(effective, afterId, Limit.of(pageSize + 1))
                : places.findByStatusNewestFirst(effective, afterId, Limit.of(pageSize + 1));
        boolean pending = effective == PlaceStatus.PENDING;
        return KeysetPages.byId(rows, pageSize, Place::getId, page -> toQueueItems(page, pending));
    }

    private List<ModerationPlaceResponse> toQueueItems(List<Place> page, boolean withDuplicates) {
        List<PlaceSummaryResponse> cards = summaries.of(page);
        List<ModerationPlaceResponse> items = new ArrayList<>(page.size());
        for (int i = 0; i < page.size(); i++) {
            items.add(new ModerationPlaceResponse(
                    cards.get(i), withDuplicates ? nearby.possibleDuplicatesOf(page.get(i)) : List.of()));
        }
        return items;
    }

    /**
     * Màn duyệt một địa điểm (UC27 bước 2 — thêm 2026-10-19, sitemap có route {@code /admin/moderation/places/:id}).
     * Mọi trạng thái: mục vừa được người khác xử lý vẫn mở được để hiện kết quả, như màn duyệt review / yêu cầu sở hữu.
     * Nghi trùng chỉ tính khi còn PENDING (đã xử lý thì không còn gì để quyết).
     */
    @Transactional(readOnly = true)
    public ModerationPlaceDetailResponse detail(Long placeId, Long moderatorId) {
        Place place = places.findDetailById(placeId).orElseThrow(PlaceService::placeNotFound);
        return new ModerationPlaceDetailResponse(
                placeService.detailForModerator(place, moderatorId),
                mapper.toUserSummary(place.getCreatedBy()),
                place.getCreatedAt(),
                place.getStatus() == PlaceStatus.PENDING ? nearby.possibleDuplicatesOf(place) : List.of());
    }

    @Transactional
    @AuditedAction(action = "PLACE_APPROVE", targetType = "PLACE", targetId = "#placeId")
    public void approve(Long moderatorId, Long placeId, Integer version) {
        Place place = pendingPlaceFor(moderatorId, placeId, version);
        place.setStatus(PlaceStatus.APPROVED);
        place.setRejectReason(null);
        markModerated(place, moderatorId);
        events.publishEvent(PlaceIndexChangedEvent.of(placeId));
        events.publishEvent(new ContributionChangedEvent(place.getCreatedBy().getId())); // +15 cho người đề xuất
        notifications.placeModerated(place, true, null);
    }

    /** Lý do bắt buộc — người đề xuất thấy lý do trên trang địa điểm / trang cá nhân. */
    @Transactional
    @AuditedAction(
            action = "PLACE_REJECT",
            targetType = "PLACE",
            targetId = "#placeId",
            metadata = "{reason: #reason.strip()}")
    public void reject(Long moderatorId, Long placeId, String reason, Integer version) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "reason", "Cần nêu lý do khi từ chối.");
        }
        Place place = pendingPlaceFor(moderatorId, placeId, version);
        place.setStatus(PlaceStatus.REJECTED);
        place.setRejectReason(reason.strip());
        markModerated(place, moderatorId);
        notifications.placeModerated(place, false, reason.strip());
    }

    private Place pendingPlaceFor(Long moderatorId, Long placeId, Integer version) {
        Place place = places.findById(placeId).orElseThrow(PlaceService::placeNotFound);
        if (place.getStatus() != PlaceStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.PLACE_ALREADY_MODERATED,
                    "Địa điểm này đã được xử lý (" + place.getStatus().name() + ").");
        }
        PlaceService.checkVersion(place, version);
        if (place.getCreatedBy().getId().equals(moderatorId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.SELF_ACTION_FORBIDDEN,
                    "Không thể tự duyệt địa điểm do chính mình đề xuất.");
        }
        return place;
    }

    private void markModerated(Place place, Long moderatorId) {
        place.setModeratedBy(users.getReferenceById(moderatorId));
        place.setModeratedAt(clock.instant());
    }
}
