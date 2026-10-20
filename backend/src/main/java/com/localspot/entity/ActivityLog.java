package com.localspot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Nhật ký thao tác quản trị (FR-42), ghi bởi aspect quanh {@code @AuditedAction}. Chỉ thêm, không sửa. */
@Entity
@Table(name = "activity_log")
public class ActivityLog extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Column(nullable = false, length = 50, updatable = false)
    private String action;

    @Column(name = "target_type", nullable = false, length = 20, updatable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private Map<String, Object> metadata;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    protected ActivityLog() {}

    public ActivityLog(
            User actor,
            String action,
            String targetType,
            Long targetId,
            Map<String, Object> metadata,
            String ipAddress) {
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.metadata = metadata == null ? null : new HashMap<>(metadata);
        this.ipAddress = ipAddress;
    }

    public Long getId() {
        return id;
    }

    public User getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
