package com.localspot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.LastModifiedDate;

/** Bảng nghiệp vụ sửa được: thêm {@code updated_at}. */
@MappedSuperclass
public abstract class AuditableEntity extends CreatedAtEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
