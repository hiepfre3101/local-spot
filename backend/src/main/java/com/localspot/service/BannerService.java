package com.localspot.service;

import com.localspot.audit.AuditedAction;
import com.localspot.config.StorageProperties;
import com.localspot.dto.request.BannerUpdateRequest;
import com.localspot.dto.response.BannerResponse;
import com.localspot.dto.response.HomeBannerResponse;
import com.localspot.dto.response.PhotoResponse;
import com.localspot.dto.response.UserSummary;
import com.localspot.entity.BannerSlot;
import com.localspot.entity.HomeBanner;
import com.localspot.entity.User;
import com.localspot.event.BannerImageStoredEvent;
import com.localspot.event.HomeBannersChangedEvent;
import com.localspot.event.PhotoObjectsDeletedEvent;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.HomeBannerRepository;
import com.localspot.repository.UserRepository;
import com.localspot.service.ImageInspector.Inspected;
import com.localspot.service.ImageResizer.Resized;
import com.localspot.service.ImageResizer.UnreadableImageException;
import com.localspot.storage.ObjectStorage;
import com.localspot.storage.StorageException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ảnh trang chủ do quản trị viên quản lý (FR-43, UC34 — mở lại Q4, supervisor chốt 2026-10-17). Phạm vi tối thiểu: ba ô
 * cố định ({@link BannerSlot}), mỗi ô một ảnh + mô tả + bật / tắt; hero chỉ đổi ảnh.
 *
 * <ul>
 *   <li><b>Xử lý ảnh ngay trong request</b>, không qua hàng đợi như ảnh địa điểm / review: chỉ quản trị viên tải, rất
 *       thưa, cần xem kết quả ngay; trang chủ công khai không bao giờ thấy trạng thái "đang xử lý". Dùng chung
 *       {@link ImageInspector} (JPEG / PNG, 5 MB, 40 MP) và {@link ImageResizer} (3 bản JPEG, bỏ EXIF) — ảnh gốc không
 *       lưu.
 *   <li>Ảnh mới lên kho trước khi ghi CSDL: rollback → xóa ({@link BannerImageStoredEvent}); ảnh cũ chỉ xóa sau commit
 *       ({@link PhotoObjectsDeletedEvent}) — trang chủ không bao giờ trỏ vào ảnh đã mất.
 *   <li>Mọi thay đổi xóa cache {@code GET /home} sau commit ({@link HomeBannersChangedEvent}) và ghi {@code activity_log}.
 * </ul>
 */
@Service
public class BannerService {

    static final String FOLDER = "banners";
    private static final String JPEG = "image/jpeg";

    private final HomeBannerRepository banners;
    private final UserRepository users;
    private final ImageInspector inspector;
    private final ImageResizer resizer;
    private final ObjectStorage storage;
    private final StorageProperties storageProperties;
    private final ApplicationEventPublisher events;

    public BannerService(
            HomeBannerRepository banners,
            UserRepository users,
            ImageInspector inspector,
            ImageResizer resizer,
            ObjectStorage storage,
            StorageProperties storageProperties,
            ApplicationEventPublisher events) {
        this.banners = banners;
        this.users = users;
        this.inspector = inspector;
        this.resizer = resizer;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.events = events;
    }

    // ─── Trang chủ ──────────────────────────────────────────────────────────

    /** Ảnh đang bật, theo thứ tự ô — gộp vào {@code GET /home} (cache chung). */
    @Transactional(readOnly = true)
    public List<HomeBannerResponse> active() {
        return banners.findByActiveTrue().stream()
                .sorted((a, b) -> a.getSlot().compareTo(b.getSlot()))
                .map(b -> new HomeBannerResponse(b.getSlot(), imageOf(b), b.getAltText()))
                .toList();
    }

    // ─── Quản trị ───────────────────────────────────────────────────────────

    /** Đủ ba ô (ô chưa có ảnh vẫn có mặt) để màn quản trị vẽ cố định. */
    @Transactional(readOnly = true)
    public List<BannerResponse> list() {
        Map<BannerSlot, BannerResponse> bySlot = new EnumMap<>(BannerSlot.class);
        for (BannerSlot slot : BannerSlot.values()) {
            bySlot.put(slot, BannerResponse.empty(slot));
        }
        banners.findAll().forEach(b -> bySlot.put(b.getSlot(), toResponse(b)));
        return List.copyOf(bySlot.values());
    }

    /**
     * Thay ảnh một ô (tạo nếu chưa có). Ô mới mặc định bật. {@code altText} {@code null} = giữ mô tả cũ. Ảnh không đọc
     * được / sai định dạng → 422 trường {@code image}; kho lỗi → 503.
     */
    @Transactional
    @AuditedAction(
            action = "BANNER_IMAGE_REPLACE",
            targetType = "BANNER",
            targetId = "#result.id()",
            metadata = "{slot: #slot.name()}")
    public BannerResponse replaceImage(Long adminId, BannerSlot slot, MultipartFile image, String altText) {
        if (image == null) {
            throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "image", "Chọn một ảnh.");
        }
        Inspected inspected = inspector.inspect(image, "image");
        Resized resized;
        try {
            resized = resizer.resize(inspected.content());
        } catch (UnreadableImageException e) {
            throw ApiException.fieldError(ErrorCode.INVALID_IMAGE, "image", "Ảnh hỏng hoặc không đọc được.");
        }
        String alt = altText == null ? null : checkAlt(altText);
        User admin = users.getReferenceById(adminId);

        String storageKey = PhotoFiles.newStorageKey(FOLDER);
        List<String> stored = new ArrayList<>();
        try {
            resized.variants().forEach((size, bytes) -> {
                String key = size.keyOf(storageKey);
                storage.put(key, bytes, JPEG);
                stored.add(key);
            });
        } catch (StorageException e) {
            deleteQuietly(stored);
            throw PhotoFiles.storageUnavailable();
        }
        events.publishEvent(new BannerImageStoredEvent(stored));

        HomeBanner banner = banners.findBySlotForUpdate(slot).orElse(null);
        if (banner == null) {
            banner = new HomeBanner(slot, storageKey, admin);
        } else {
            events.publishEvent(new PhotoObjectsDeletedEvent(variantKeys(banner.getStorageKey())));
            banner.setStorageKey(storageKey);
            banner.setUpdatedBy(admin);
        }
        if (alt != null) {
            banner.setAltText(alt);
        }
        banner = banners.saveAndFlush(banner);
        events.publishEvent(new HomeBannersChangedEvent(slot));
        return toResponse(banner);
    }

    /** Sửa mô tả / bật tắt. Ô chưa có ảnh → 404; {@code version} lệch → 409. */
    @Transactional
    @AuditedAction(
            action = "BANNER_UPDATE",
            targetType = "BANNER",
            targetId = "#result.id()",
            metadata = "{slot: #slot.name(), active: #result.active()}")
    public BannerResponse update(Long adminId, BannerSlot slot, BannerUpdateRequest request) {
        HomeBanner banner = banners.findBySlot(slot).orElseThrow(BannerService::bannerNotFound);
        if (request.version() != banner.getVersion()) {
            throw concurrentModification();
        }
        if (request.altText() != null) {
            banner.setAltText(checkAlt(request.altText()));
        }
        if (request.active() != null) {
            banner.setActive(request.active());
        }
        banner.setUpdatedBy(users.getReferenceById(adminId));
        banners.flush(); // UPDATE ngay → version mới trong response
        events.publishEvent(new HomeBannersChangedEvent(slot));
        return toResponse(banner);
    }

    /** Gỡ ảnh — ô về hình minh hoạ mặc định; object trên kho xóa sau commit. Ô chưa có ảnh → 404. */
    @Transactional
    @AuditedAction(
            action = "BANNER_REMOVE",
            targetType = "BANNER",
            targetId = "#result",
            metadata = "{slot: #slot.name()}")
    public Long remove(BannerSlot slot) {
        HomeBanner banner = banners.findBySlotForUpdate(slot).orElseThrow(BannerService::bannerNotFound);
        Long id = banner.getId();
        banners.delete(banner);
        events.publishEvent(new PhotoObjectsDeletedEvent(variantKeys(banner.getStorageKey())));
        events.publishEvent(new HomeBannersChangedEvent(slot));
        return id;
    }

    // ─── Hỗ trợ ─────────────────────────────────────────────────────────────

    private BannerResponse toResponse(HomeBanner banner) {
        User by = banner.getUpdatedBy();
        return new BannerResponse(
                banner.getSlot(),
                banner.getId(),
                imageOf(banner),
                banner.getAltText(),
                banner.isActive(),
                banner.getVersion(),
                banner.getUpdatedAt(),
                new UserSummary(by.getId(), by.getDisplayName(), by.getAvatarUrl()));
    }

    /** Cùng dạng {@code Photo} với ảnh địa điểm (3 cỡ) — luôn READY vì xử lý xong trong request. */
    private PhotoResponse imageOf(HomeBanner banner) {
        String key = banner.getStorageKey();
        return new PhotoResponse(
                banner.getId(),
                "READY",
                storageProperties.publicUrlOf(PhotoSize.THUMB.keyOf(key)),
                storageProperties.publicUrlOf(PhotoSize.MEDIUM.keyOf(key)),
                storageProperties.publicUrlOf(PhotoSize.LARGE.keyOf(key)));
    }

    private static List<String> variantKeys(String storageKey) {
        return Arrays.stream(PhotoSize.values())
                .map(size -> size.keyOf(storageKey))
                .toList();
    }

    private static String checkAlt(String altText) {
        String alt = altText.strip();
        if (alt.length() > 200) {
            throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "altText", "Mô tả ảnh tối đa 200 ký tự.");
        }
        return alt;
    }

    private void deleteQuietly(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        try {
            storage.delete(keys);
        } catch (StorageException ignored) {
            // Kho đang lỗi — object mồ côi chỉ tốn chỗ, không ảnh hưởng trang chủ (CSDL không trỏ tới)
        }
    }

    static ApiException bannerNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.BANNER_NOT_FOUND, "Ô này chưa có ảnh.");
    }

    private static ApiException concurrentModification() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ErrorCode.CONCURRENT_MODIFICATION,
                "Ảnh vừa được cập nhật ở nơi khác. Hãy tải lại rồi thử lại.");
    }
}
