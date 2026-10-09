package com.localspot.service;

import com.localspot.amqp.PhotoProcessMessage.Target;
import com.localspot.config.PhotoProperties;
import com.localspot.dto.response.PhotoResponse;
import com.localspot.entity.PhotoStatus;
import com.localspot.entity.Place;
import com.localspot.entity.PlacePhoto;
import com.localspot.entity.User;
import com.localspot.event.PhotoObjectsDeletedEvent;
import com.localspot.event.PhotosStoredEvent;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.PlacePhotoRepository;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.UserRepository;
import com.localspot.service.ImageInspector.Inspected;
import com.localspot.service.ImageResizer.Resized;
import com.localspot.storage.StorageException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ảnh địa điểm (FR-16, checklist E2): nhận upload → lưu ảnh gốc vào {@code incoming/…} + bản ghi PROCESSING → sau commit
 * đưa id vào queue {@code photo.process} → consumer {@link #process} sinh 3 bản JPEG vào {@code places/…} → READY.
 *
 * <p>Kiểm tra upload, lưu ảnh gốc, sinh các bản và URL dùng chung với ảnh review — {@link PhotoFiles}
 * ({@code storage_key = places/{placeId}/{uuid}}).
 *
 * <p><b>Ảnh bìa</b>: ảnh đầu tiên của địa điểm chưa có bìa; bìa bị xóa / xử lý lỗi → chuyển cho ảnh kế tiếp theo
 * {@code sort_order}. Chủ chọn bìa không có trong openapi — để sau nếu cần.
 */
@Service
public class PlacePhotoService {

    private static final Logger log = LoggerFactory.getLogger(PlacePhotoService.class);

    private final PlacePhotoRepository photos;
    private final PlaceRepository places;
    private final UserRepository users;
    private final PhotoFiles files;
    private final PhotoProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    public PlacePhotoService(
            PlacePhotoRepository photos,
            PlaceRepository places,
            UserRepository users,
            PhotoFiles files,
            PhotoProperties properties,
            ApplicationEventPublisher events,
            TransactionTemplate tx) {
        this.photos = photos;
        this.places = places;
        this.users = users;
        this.files = files;
        this.properties = properties;
        this.events = events;
        this.tx = tx;
    }

    // ─── Nhận upload ─────────────────────────────────────────────────────────

    /** Kiểm tra cả lô trước khi ghi gì (NFR-09) — xem {@link PhotoFiles#inspect}. */
    public List<Inspected> inspect(List<MultipartFile> uploads) {
        return files.inspect(uploads);
    }

    /**
     * Lưu ảnh đã kiểm tra cho một địa điểm — phải chạy trong transaction của thao tác gọi (đề xuất địa điểm / chủ thêm
     * ảnh) để ảnh và địa điểm cùng commit hoặc cùng rollback.
     *
     * <p>Ảnh gốc lên kho ngay trong request (giữ kết nối CSDL trong lúc upload — tối đa 10 × 5 MB; chấp nhận được ở quy
     * mô đồ án, đổi lại không cần kho tạm thứ hai). Rollback → ảnh gốc bị xóa ở {@code PhotoQueuePublisher}.
     */
    @Transactional
    public List<PhotoResponse> store(Long placeId, Long uploaderId, List<Inspected> images) {
        if (images.isEmpty()) {
            return List.of();
        }
        Place place = places.findByIdForUpdate(placeId).orElseThrow(PlaceService::placeNotFound);
        long existing = photos.countByPlaceIdAndStatusNot(placeId, PhotoStatus.FAILED);
        if (existing + images.size() > properties.maxPerPlace()) {
            throw ApiException.fieldError(
                    ErrorCode.PHOTO_LIMIT_EXCEEDED,
                    "photos",
                    "Mỗi địa điểm tối đa " + properties.maxPerPlace() + " ảnh (đang có " + existing + ").");
        }
        User uploader = users.getReferenceById(uploaderId);
        boolean needsCover = !photos.existsByPlaceIdAndCoverTrue(placeId);
        int sortOrder = photos.maxSortOrder(placeId);

        List<PlacePhoto> saved = new ArrayList<>(images.size());
        List<String> incomingKeys = new ArrayList<>(images.size());
        try {
            for (Inspected image : images) {
                String storageKey = PhotoFiles.newStorageKey("places/" + placeId);
                incomingKeys.add(files.putOriginal(storageKey, image));

                PlacePhoto photo = new PlacePhoto(place, uploader, storageKey);
                photo.setSortOrder(++sortOrder);
                photo.setCover(needsCover);
                needsCover = false;
                saved.add(photos.save(photo));
            }
        } catch (StorageException e) {
            log.error("Không lưu được ảnh gốc của địa điểm {}: {}", placeId, e.getMessage());
            // Ảnh đã lên kho trước lỗi vẫn được dọn: sự kiện phát trước khi ném → rollback kích hoạt xóa
            events.publishEvent(new PhotosStoredEvent(Target.PLACE, List.of(), incomingKeys));
            throw PhotoFiles.storageUnavailable();
        }
        events.publishEvent(new PhotosStoredEvent(
                Target.PLACE, saved.stream().map(PlacePhoto::getId).toList(), incomingKeys));
        return saved.stream().map(this::toResponse).toList();
    }

    /** Chủ xóa ảnh (openapi {@code DELETE /owner/places/{placeId}/photos/{photoId}}); quyền sở hữu kiểm ở controller. */
    @Transactional
    public void delete(Long placeId, Long photoId) {
        places.findByIdForUpdate(placeId).orElseThrow(PlaceService::placeNotFound);
        PlacePhoto photo = photos.findByIdAndPlaceId(photoId, placeId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PHOTO_NOT_FOUND, "Không tìm thấy ảnh."));
        boolean wasCover = photo.isCover();
        photos.delete(photo);
        photos.flush();
        if (wasCover) {
            reassignCover(placeId);
        }
        events.publishEvent(new PhotoObjectsDeletedEvent(PhotoFiles.objectKeysOf(photo.getStorageKey())));
    }

    // ─── Xử lý nền (consumer) ───────────────────────────────────────────────

    /**
     * Sinh các bản ảnh cho một bản ghi PROCESSING. Không giữ transaction trong lúc đọc kho / resize (vài trăm ms tới vài
     * giây) — chỉ mở transaction ngắn để đọc và cập nhật trạng thái.
     *
     * <ul>
     *   <li>Ảnh không còn / không còn PROCESSING (đã xử lý, đã xóa, message lặp) → bỏ qua: xử lý lặp lại an toàn.
     *   <li>Ảnh không giải mã được → FAILED ngay, không retry (thử lại vẫn hỏng).
     *   <li>Kho lỗi → ném {@link StorageException}: listener retry, hết lượt sang {@code photo.process.dlq} (NFR-13);
     *       ảnh giữ PROCESSING tới khi chạy lại message bằng tay.
     * </ul>
     */
    public void process(long photoId) {
        String storageKey = tx.execute(status -> photos.findById(photoId)
                .filter(p -> p.getStatus() == PhotoStatus.PROCESSING)
                .map(PlacePhoto::getStorageKey)
                .orElse(null));
        if (storageKey == null) {
            log.debug("Bỏ qua ảnh {}: không còn chờ xử lý", photoId);
            return;
        }
        Optional<Resized> resized = files.render(photoId, storageKey);
        if (resized.isEmpty()) {
            tx.executeWithoutResult(status -> photos.findById(photoId).ifPresent(this::fail));
            return;
        }
        tx.executeWithoutResult(status -> photos.findById(photoId)
                .ifPresent(p -> p.markReady(resized.get().width(), resized.get().height())));
        files.discardOriginal(storageKey);
    }

    private void fail(PlacePhoto photo) {
        photo.markFailed();
        if (photo.isCover()) {
            photo.setCover(false);
            photos.flush();
            reassignCover(photo.getPlace().getId());
        }
    }

    /** Bìa mới = ảnh chưa lỗi đầu tiên theo thứ tự hiển thị; không còn ảnh nào → không có bìa. */
    private void reassignCover(Long placeId) {
        photos.findByPlaceIdOrderBySortOrderAscIdAsc(placeId).stream()
                .filter(p -> p.getStatus() != PhotoStatus.FAILED)
                .findFirst()
                .ifPresent(p -> p.setCover(true));
    }

    // ─── Đọc ────────────────────────────────────────────────────────────────

    /**
     * Gallery cho trang chi tiết. Người ngoài chỉ thấy ảnh READY; người đề xuất / chủ / kiểm duyệt viên thấy cả ảnh đang
     * xử lý / lỗi (kèm {@code status}) để biết ảnh mình vừa tải lên đang ở đâu.
     */
    public List<PhotoResponse> gallery(Long placeId, boolean includeUnready) {
        return photos.findByPlaceIdOrderBySortOrderAscIdAsc(placeId).stream()
                .filter(p -> includeUnready || p.getStatus() == PhotoStatus.READY)
                .map(this::toResponse)
                .toList();
    }

    /** Ảnh bìa đã xử lý xong; {@code null} nếu chưa có. */
    public PhotoResponse cover(Long placeId) {
        Map<Long, PhotoResponse> covers = covers(List.of(placeId));
        return covers.get(placeId);
    }

    /** Ảnh bìa của nhiều địa điểm (thẻ trong danh sách) — một truy vấn. */
    public Map<Long, PhotoResponse> covers(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PhotoResponse> byPlace = new HashMap<>();
        for (PlacePhoto photo : photos.findReadyCovers(placeIds)) {
            byPlace.put(photo.getPlace().getId(), toResponse(photo));
        }
        return byPlace;
    }

    PhotoResponse toResponse(PlacePhoto photo) {
        return files.toResponse(photo.getId(), photo.getStatus(), photo.getStorageKey());
    }
}
