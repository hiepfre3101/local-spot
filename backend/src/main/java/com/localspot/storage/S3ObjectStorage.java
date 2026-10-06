package com.localspot.storage;

import com.localspot.config.StorageProperties;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

/** {@link ObjectStorage} trên S3 API — cùng mã cho MinIO (dev) và R2 / S3 (deploy), chỉ khác cấu hình. */
@Component
public class S3ObjectStorage implements ObjectStorage {

    /** Giới hạn của DeleteObjects. */
    private static final int DELETE_BATCH = 1000;

    private final S3Client s3;
    private final String bucket;

    public S3ObjectStorage(S3Client s3, StorageProperties properties) {
        this.s3 = s3;
        this.bucket = properties.bucket();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new StorageException("Không ghi được object " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
        } catch (SdkException e) {
            throw new StorageException("Không đọc được object " + key, e);
        }
    }

    @Override
    public void delete(Collection<String> keys) {
        List<ObjectIdentifier> ids = keys.stream()
                .map(k -> ObjectIdentifier.builder().key(k).build())
                .toList();
        try {
            for (int from = 0; from < ids.size(); from += DELETE_BATCH) {
                List<ObjectIdentifier> batch = ids.subList(from, Math.min(from + DELETE_BATCH, ids.size()));
                s3.deleteObjects(b -> b.bucket(bucket)
                        .delete(Delete.builder().objects(batch).quiet(true).build()));
            }
        } catch (SdkException e) {
            throw new StorageException("Không xóa được " + keys.size() + " object", e);
        }
    }
}
