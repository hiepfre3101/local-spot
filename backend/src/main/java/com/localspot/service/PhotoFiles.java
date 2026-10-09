package com.localspot.service;

import com.localspot.config.PhotoProperties;
import com.localspot.config.StorageProperties;
import com.localspot.dto.response.PhotoResponse;
import com.localspot.entity.PhotoStatus;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.service.ImageInspector.Inspected;
import com.localspot.service.ImageResizer.Resized;
import com.localspot.service.ImageResizer.UnreadableImageException;
import com.localspot.storage.ObjectStorage;
import com.localspot.storage.StorageException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Phần dùng chung của ảnh địa điểm (E2) và ảnh review (E7): kiểm tra upload, ảnh gốc riêng tư ở {@code incoming/…}, sinh
 * 3 bản JPEG công khai, URL. Bản ghi (bảng, trạng thái, giới hạn số ảnh) thuộc service của từng loại.
 *
 * <p><b>Bố cục key</b>: {@code storage_key = {places|reviews}/{id}/{uuid}}; ảnh gốc {@code incoming/} + storage_key
 * (riêng tư, còn EXIF); các bản {@link PhotoSize#keyOf} (đọc công khai). UUID ngẫu nhiên → không đoán được URL ảnh của
 * nội dung chưa công khai.
 */
@Component
public class PhotoFiles {

    private static final Logger log = LoggerFactory.getLogger(PhotoFiles.class);

    static final String INCOMING_PREFIX = "incoming/";
    private static final String JPEG = "image/jpeg";

    private final ImageInspector inspector;
    private final ImageResizer resizer;
    private final ObjectStorage storage;
    private final PhotoProperties properties;
    private final StorageProperties storageProperties;

    public PhotoFiles(
            ImageInspector inspector,
            ImageResizer resizer,
            ObjectStorage storage,
            PhotoProperties properties,
            StorageProperties storageProperties) {
        this.inspector = inspector;
        this.resizer = resizer;
        this.storage = storage;
        this.properties = properties;
        this.storageProperties = storageProperties;
    }

    /**
     * Kiểm tra cả lô trước khi ghi gì (NFR-09) — một ảnh hỏng thì cả request bị từ chối, lỗi chỉ đúng {@code photos[i]}.
     * {@code null} / rỗng = không có ảnh.
     */
    public List<Inspected> inspect(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        if (files.size() > properties.maxPerRequest()) {
            throw ApiException.fieldError(
                    ErrorCode.PHOTO_LIMIT_EXCEEDED,
                    "photos",
                    "Tối đa " + properties.maxPerRequest() + " ảnh mỗi lần tải lên.");
        }
        List<Inspected> images = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            images.add(inspector.inspect(files.get(i), "photos[" + i + "]"));
        }
        return images;
    }

    /** Key mới cho một ảnh trong thư mục {@code folder} ({@code places/12}, {@code reviews/34}). */
    public static String newStorageKey(String folder) {
        return folder + "/" + UUID.randomUUID();
    }

    /** Lưu ảnh gốc (riêng tư) — trả key đã ghi để dọn khi transaction rollback. Kho lỗi → {@link StorageException}. */
    public String putOriginal(String storageKey, Inspected image) {
        String incomingKey = INCOMING_PREFIX + storageKey;
        storage.put(incomingKey, image.content(), image.format().contentType());
        return incomingKey;
    }

    /**
     * Sinh và lưu các bản của một ảnh. Rỗng = ảnh không giải mã được (ảnh gốc đã bị xóa — thử lại vẫn hỏng). Kho lỗi →
     * {@link StorageException} cho listener retry. Ảnh gốc chỉ xóa ({@link #discardOriginal}) sau khi bản ghi đã READY.
     */
    public Optional<Resized> render(long photoId, String storageKey) {
        String incomingKey = INCOMING_PREFIX + storageKey;
        byte[] original = storage.get(incomingKey);
        Resized resized;
        try {
            resized = resizer.resize(original);
        } catch (UnreadableImageException e) {
            log.warn("Ảnh {} ({}) không xử lý được, đánh dấu FAILED: {}", photoId, storageKey, e.getMessage());
            discardOriginal(storageKey);
            return Optional.empty();
        }
        resized.variants().forEach((size, bytes) -> storage.put(size.keyOf(storageKey), bytes, JPEG));
        return Optional.of(resized);
    }

    public void discardOriginal(String storageKey) {
        deleteQuietly(List.of(INCOMING_PREFIX + storageKey));
    }

    /** URL chỉ có khi READY — trước đó object các bản chưa tồn tại. */
    public PhotoResponse toResponse(Long id, PhotoStatus status, String storageKey) {
        if (status != PhotoStatus.READY) {
            return new PhotoResponse(id, status.name(), null, null, null);
        }
        return new PhotoResponse(
                id,
                status.name(),
                storageProperties.publicUrlOf(PhotoSize.THUMB.keyOf(storageKey)),
                storageProperties.publicUrlOf(PhotoSize.MEDIUM.keyOf(storageKey)),
                storageProperties.publicUrlOf(PhotoSize.LARGE.keyOf(storageKey)));
    }

    /** Mọi object có thể có của một ảnh: ảnh gốc (nếu chưa xử lý xong) + các bản. */
    public static List<String> objectKeysOf(String storageKey) {
        List<String> keys = new ArrayList<>();
        keys.add(INCOMING_PREFIX + storageKey);
        Arrays.stream(PhotoSize.values()).map(size -> size.keyOf(storageKey)).forEach(keys::add);
        return keys;
    }

    public static ApiException storageUnavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SERVICE_UNAVAILABLE,
                "Kho ảnh tạm thời không khả dụng. Hãy thử lại sau.");
    }

    private void deleteQuietly(List<String> keys) {
        try {
            storage.delete(keys);
        } catch (StorageException e) {
            // Chỉ còn rác trong kho, ảnh đã xử lý xong — không retry cả message vì việc dọn dẹp
            log.warn("Không xóa được {}: {}", keys, e.getMessage());
        }
    }
}
