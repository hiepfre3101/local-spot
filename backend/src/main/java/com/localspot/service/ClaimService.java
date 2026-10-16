package com.localspot.service;

import com.localspot.dto.request.ClaimCreateRequest;
import com.localspot.entity.ClaimStatus;
import com.localspot.entity.Place;
import com.localspot.entity.PlaceClaim;
import com.localspot.entity.PlaceStatus;
import com.localspot.entity.User;
import com.localspot.event.ClaimEvidenceStoredEvent;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.PlaceClaimRepository;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.UserRepository;
import com.localspot.service.ImageInspector.Inspected;
import com.localspot.storage.ObjectStorage;
import com.localspot.storage.StorageException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Thành viên gửi yêu cầu xác nhận sở hữu (UC23, FR-31). Thứ tự kiểm tra (chốt 2026-10-16): email đã xác thực (403) →
 * minh chứng 1–5 ảnh hợp lệ (422) → địa điểm APPROVED (404) → chưa có chủ (409 {@code PLACE_ALREADY_OWNED}, gồm cả khi
 * người gửi đã là chủ) → chưa có yêu cầu đang chờ của chính người này cho địa điểm (409 {@code CLAIM_ALREADY_PENDING}).
 * Người khác đang chờ không chặn — kiểm duyệt viên so các yêu cầu, duyệt một thì các yêu cầu còn lại tự bị từ chối.
 *
 * <p><b>Minh chứng</b> chỉ nhận ảnh (JPEG / PNG, cùng kiểm tra chữ ký + 5 MB + 40 MP với ảnh địa điểm — chốt
 * 2026-10-16), lưu <b>nguyên bản</b> (kiểm duyệt viên cần đọc được giấy tờ) ở {@code claims/{placeId}/{uuid}} — tiền tố
 * này không nằm trong policy đọc công khai, chỉ xem qua link tạm ({@link ClaimModerationService#evidenceLink}).
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    /** openapi {@code evidence: maxItems 5}. */
    static final int MAX_EVIDENCE = 5;

    private final PlaceClaimRepository claims;
    private final PlaceRepository places;
    private final UserRepository users;
    private final ImageInspector inspector;
    private final ObjectStorage storage;
    private final ApplicationEventPublisher events;
    private final NotificationService notifications;

    public ClaimService(
            PlaceClaimRepository claims,
            PlaceRepository places,
            UserRepository users,
            ImageInspector inspector,
            ObjectStorage storage,
            NotificationService notifications,
            ApplicationEventPublisher events) {
        this.claims = claims;
        this.places = places;
        this.users = users;
        this.inspector = inspector;
        this.storage = storage;
        this.events = events;
        this.notifications = notifications;
    }

    @Transactional
    public void create(Long userId, Long placeId, ClaimCreateRequest request, List<MultipartFile> evidence) {
        // Khóa dòng người dùng: hai lần bấm gửi cùng lúc xếp hàng ở đây → lần sau thấy yêu cầu đang chờ (409)
        User claimant = users.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng."));
        if (!claimant.isEmailVerified()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.EMAIL_NOT_VERIFIED,
                    "Hãy xác thực email trước khi gửi yêu cầu sở hữu.");
        }
        List<Inspected> images = inspect(evidence);
        Place place = places.findById(placeId)
                .filter(p -> p.getStatus() == PlaceStatus.APPROVED)
                .orElseThrow(PlaceService::placeNotFound);
        if (place.getOwner() != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.PLACE_ALREADY_OWNED,
                    "Địa điểm này đã có chủ. Nếu bạn cho rằng có nhầm lẫn, hãy liên hệ quản trị viên.");
        }
        if (claims.existsByPlaceIdAndUserIdAndStatus(placeId, userId, ClaimStatus.PENDING)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.CLAIM_ALREADY_PENDING,
                    "Bạn đã có một yêu cầu đang chờ duyệt cho địa điểm này.");
        }

        List<String> keys = new ArrayList<>(images.size());
        try {
            for (Inspected image : images) {
                String key = "claims/" + placeId + "/" + UUID.randomUUID();
                storage.put(key, image.content(), image.format().contentType());
                keys.add(key);
            }
        } catch (StorageException e) {
            log.error("Không lưu được minh chứng yêu cầu sở hữu địa điểm {}: {}", placeId, e.getMessage());
            // File đã lên trước lỗi vẫn được dọn: sự kiện phát trước khi ném → rollback kích hoạt xóa
            events.publishEvent(new ClaimEvidenceStoredEvent(keys));
            throw PhotoFiles.storageUnavailable();
        }
        events.publishEvent(new ClaimEvidenceStoredEvent(keys));
        PlaceClaim claim = claims.save(
                new PlaceClaim(place, claimant, request.contactPhone().strip(), noteOf(request), keys));
        notifications.claimSubmitted(claim); // đội kiểm duyệt (UC23 bước 5)
    }

    /** Cả lô kiểm tra trước khi ghi gì — một file hỏng thì cả yêu cầu bị từ chối, lỗi chỉ đúng {@code evidence[i]}. */
    private List<Inspected> inspect(List<MultipartFile> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "evidence", "Cần ít nhất một ảnh minh chứng.");
        }
        if (evidence.size() > MAX_EVIDENCE) {
            throw ApiException.fieldError(
                    ErrorCode.VALIDATION_FAILED, "evidence", "Tối đa " + MAX_EVIDENCE + " ảnh minh chứng.");
        }
        List<Inspected> images = new ArrayList<>(evidence.size());
        for (int i = 0; i < evidence.size(); i++) {
            images.add(inspector.inspect(evidence.get(i), "evidence[" + i + "]"));
        }
        return images;
    }

    private static String noteOf(ClaimCreateRequest request) {
        return request.note() == null || request.note().isBlank()
                ? null
                : request.note().strip();
    }
}
