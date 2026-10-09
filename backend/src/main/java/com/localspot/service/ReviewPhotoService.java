package com.localspot.service;

import com.localspot.amqp.PhotoProcessMessage.Target;
import com.localspot.config.PhotoProperties;
import com.localspot.dto.response.PhotoResponse;
import com.localspot.entity.PhotoStatus;
import com.localspot.entity.Review;
import com.localspot.entity.ReviewPhoto;
import com.localspot.entity.User;
import com.localspot.event.PhotoObjectsDeletedEvent;
import com.localspot.event.PhotosStoredEvent;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.ReviewPhotoRepository;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ảnh đính kèm review (FR-19, checklist E7) — cùng pipeline với ảnh địa điểm ({@link PhotoFiles}, queue
 * {@code photo.process}): ảnh gốc riêng tư → PROCESSING → consumer sinh 3 bản → READY.
 *
 * <p>Luật (chốt 2026-10-09): tối đa {@code max-per-review} ảnh mỗi review tính cả ảnh thêm sau (ảnh lỗi không tính);
 * thêm / xóa ảnh không đổi trạng thái review (ảnh không duyệt lại). Ảnh chỉ hiện trong review, không gộp vào gallery của
 * địa điểm. Người khác chỉ thấy ảnh READY; tác giả thấy cả ảnh đang xử lý / lỗi.
 */
@Service
public class ReviewPhotoService {

    private static final Logger log = LoggerFactory.getLogger(ReviewPhotoService.class);

    private final ReviewPhotoRepository photos;
    private final UserRepository users;
    private final PhotoFiles files;
    private final PhotoProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    public ReviewPhotoService(
            ReviewPhotoRepository photos,
            UserRepository users,
            PhotoFiles files,
            PhotoProperties properties,
            ApplicationEventPublisher events,
            TransactionTemplate tx) {
        this.photos = photos;
        this.users = users;
        this.files = files;
        this.properties = properties;
        this.events = events;
        this.tx = tx;
    }

    /** Kiểm tra cả lô trước khi ghi gì (NFR-09) — xem {@link PhotoFiles#inspect}. */
    public List<Inspected> inspect(List<MultipartFile> uploads) {
        return files.inspect(uploads);
    }

    /**
     * Lưu ảnh đã kiểm tra cho một review — chạy trong transaction của thao tác gọi (viết review / thêm ảnh), review đã
     * được khóa dòng hoặc vừa tạo. Rollback → ảnh gốc bị xóa ở {@code PhotoQueuePublisher}.
     */
    public List<PhotoResponse> store(Review review, Long uploaderId, List<Inspected> images) {
        if (images.isEmpty()) {
            return List.of();
        }
        long existing = photos.countByReviewIdAndStatusNot(review.getId(), PhotoStatus.FAILED);
        if (existing + images.size() > properties.maxPerReview()) {
            throw ApiException.fieldError(
                    ErrorCode.PHOTO_LIMIT_EXCEEDED,
                    "photos",
                    "Mỗi đánh giá tối đa " + properties.maxPerReview() + " ảnh (đang có " + existing + ").");
        }
        User uploader = users.getReferenceById(uploaderId);
        int sortOrder = photos.maxSortOrder(review.getId());
        List<ReviewPhoto> saved = new ArrayList<>(images.size());
        List<String> incomingKeys = new ArrayList<>(images.size());
        try {
            for (Inspected image : images) {
                String storageKey = PhotoFiles.newStorageKey("reviews/" + review.getId());
                incomingKeys.add(files.putOriginal(storageKey, image));
                saved.add(photos.save(new ReviewPhoto(review, uploader, storageKey, ++sortOrder)));
            }
        } catch (StorageException e) {
            log.error("Không lưu được ảnh gốc của review {}: {}", review.getId(), e.getMessage());
            events.publishEvent(new PhotosStoredEvent(Target.REVIEW, List.of(), incomingKeys));
            throw PhotoFiles.storageUnavailable();
        }
        events.publishEvent(new PhotosStoredEvent(
                Target.REVIEW, saved.stream().map(ReviewPhoto::getId).toList(), incomingKeys));
        return saved.stream().map(this::toResponse).toList();
    }

    /** Xóa một ảnh của review; review đã được khóa dòng bởi người gọi. Object xóa sau commit. */
    public void delete(Long reviewId, Long photoId) {
        ReviewPhoto photo = photos.findByIdAndReviewId(photoId, reviewId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PHOTO_NOT_FOUND, "Không tìm thấy ảnh."));
        photos.delete(photo);
        events.publishEvent(new PhotoObjectsDeletedEvent(PhotoFiles.objectKeysOf(photo.getStorageKey())));
    }

    /** Consumer — như {@code PlacePhotoService#process}: bỏ qua ảnh không còn PROCESSING, ảnh hỏng → FAILED. */
    public void process(long photoId) {
        String storageKey = tx.execute(status -> photos.findById(photoId)
                .filter(p -> p.getStatus() == PhotoStatus.PROCESSING)
                .map(ReviewPhoto::getStorageKey)
                .orElse(null));
        if (storageKey == null) {
            log.debug("Bỏ qua ảnh review {}: không còn chờ xử lý", photoId);
            return;
        }
        Optional<Resized> resized = files.render(photoId, storageKey);
        if (resized.isEmpty()) {
            tx.executeWithoutResult(status -> photos.findById(photoId).ifPresent(ReviewPhoto::markFailed));
            return;
        }
        tx.executeWithoutResult(status -> photos.findById(photoId)
                .ifPresent(p -> p.markReady(resized.get().width(), resized.get().height())));
        files.discardOriginal(storageKey);
    }

    /**
     * Ảnh của cả trang review theo id review — một truy vấn. {@code viewerId} là tác giả của review nào thì thấy cả ảnh
     * chưa READY của review đó.
     */
    public Map<Long, List<PhotoResponse>> photosOf(Collection<Review> reviews, Long viewerId) {
        if (reviews.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> authorOf = new HashMap<>();
        reviews.forEach(r -> authorOf.put(r.getId(), r.getUser().getId()));
        Map<Long, List<PhotoResponse>> byReview = new HashMap<>();
        for (ReviewPhoto photo : photos.findByReviewIds(authorOf.keySet())) {
            Long reviewId = photo.getReview().getId();
            boolean author = authorOf.get(reviewId).equals(viewerId);
            if (author || photo.getStatus() == PhotoStatus.READY) {
                byReview.computeIfAbsent(reviewId, id -> new ArrayList<>()).add(toResponse(photo));
            }
        }
        return byReview;
    }

    private PhotoResponse toResponse(ReviewPhoto photo) {
        return files.toResponse(photo.getId(), photo.getStatus(), photo.getStorageKey());
    }
}
