package com.localspot.service;

import com.localspot.audit.AuditedAction;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.entity.Place;
import com.localspot.entity.PlaceStatus;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.mapper.PlaceMapper;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.UserRepository;
import java.time.Clock;
import java.util.List;
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
 * Mỗi quyết định ghi {@code activity_log} qua {@link AuditedAction} (FR-42). Thông báo cho người đề xuất (UC11 hậu điều
 * kiện) và đồng bộ chỉ mục tìm kiếm (FR-35) gắn vào đây ở các mục thông báo / Meilisearch.
 */
@Service
public class PlaceModerationService {

    private final PlaceRepository places;
    private final UserRepository users;
    private final PlaceMapper mapper;
    private final Clock clock;

    public PlaceModerationService(PlaceRepository places, UserRepository users, PlaceMapper mapper, Clock clock) {
        this.places = places;
        this.users = users;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Hàng chờ theo trạng thái ({@code null} = PENDING). PENDING xếp FIFO — cũ nhất trước, như hàng chờ gộp; trạng thái
     * đã xử lý là lịch sử nên mới nhất trước.
     */
    @Transactional(readOnly = true)
    public CursorPage<PlaceSummaryResponse> queue(PlaceStatus status, String cursor, Integer limit) {
        PlaceStatus effective = status == null ? PlaceStatus.PENDING : status;
        int pageSize = KeysetCursor.limit(limit);
        Long afterId = KeysetCursor.decode(cursor);
        List<Place> rows = effective == PlaceStatus.PENDING
                ? places.findByStatusOldestFirst(effective, afterId, Limit.of(pageSize + 1))
                : places.findByStatusNewestFirst(effective, afterId, Limit.of(pageSize + 1));
        return KeysetPages.byId(rows, pageSize, Place::getId, mapper::toSummaries);
    }

    @Transactional
    @AuditedAction(action = "PLACE_APPROVE", targetType = "PLACE", targetId = "#placeId")
    public void approve(Long moderatorId, Long placeId, Integer version) {
        Place place = pendingPlaceFor(moderatorId, placeId, version);
        place.setStatus(PlaceStatus.APPROVED);
        place.setRejectReason(null);
        markModerated(place, moderatorId);
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
