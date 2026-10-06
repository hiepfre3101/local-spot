package com.localspot.storage;

import java.util.Collection;

/**
 * Kho object (ảnh). Tách interface khỏi S3 SDK để service không phụ thuộc nhà cung cấp và test thay bằng bản trong bộ
 * nhớ — không cần container MinIO trong CI.
 *
 * <p>Lỗi kết nối / phía kho ném {@link StorageException} (unchecked): consumer để listener retry, request trả 503.
 */
public interface ObjectStorage {

    void put(String key, byte[] content, String contentType);

    /** Nội dung object; không tồn tại → {@link StorageException}. */
    byte[] get(String key);

    /** Xóa nhiều object; key không tồn tại được bỏ qua (xóa lặp lại an toàn). */
    void delete(Collection<String> keys);
}
