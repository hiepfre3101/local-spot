package com.localspot.storage;

import com.localspot.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Dev: tạo bucket và policy đọc công khai khi khởi động, để {@code docker compose up -d} xong là upload được ngay. Deploy
 * tạo bucket bằng tay / hạ tầng (R2 bật public qua custom domain) — tắt bằng {@code localspot.storage.create-bucket}.
 *
 * <p>Chỉ {@code places/*} và {@code reviews/*} (ảnh đã xử lý, không EXIF) được đọc công khai; ảnh gốc ở
 * {@code incoming/*} còn EXIF (có thể có GPS — NFR-11) nên giữ riêng tư.
 */
@Component
@ConditionalOnBooleanProperty("localspot.storage.create-bucket")
public class StorageBucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StorageBucketInitializer.class);

    private static final String PUBLIC_READ_POLICY = """
            {
              "Version": "2012-10-17",
              "Statement": [{
                "Effect": "Allow",
                "Principal": {"AWS": ["*"]},
                "Action": ["s3:GetObject"],
                "Resource": ["arn:aws:s3:::{bucket}/places/*", "arn:aws:s3:::{bucket}/reviews/*"]
              }]
            }
            """;

    private final S3Client s3;
    private final String bucket;

    public StorageBucketInitializer(S3Client s3, StorageProperties properties) {
        this.s3 = s3;
        this.bucket = properties.bucket();
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            try {
                s3.headBucket(b -> b.bucket(bucket));
            } catch (NoSuchBucketException e) {
                s3.createBucket(b -> b.bucket(bucket));
                log.info("Đã tạo bucket ảnh '{}'", bucket);
            }
            s3.putBucketPolicy(b -> b.bucket(bucket).policy(PUBLIC_READ_POLICY.replace("{bucket}", bucket)));
        } catch (SdkException e) {
            // Kho ảnh chưa chạy không chặn khởi động: các chức năng khác vẫn dùng được, upload sẽ trả 503
            log.warn("Không chuẩn bị được bucket ảnh '{}': {}", bucket, e.getMessage());
        }
    }
}
