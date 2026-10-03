package com.localspot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Vi phạm tính vào penalty_score của trust (requirements §5.1). {@code points} chụp mức phạt lúc ghi để đổi cấu hình
 * không làm sai lịch sử; hết hiệu lực tại {@code expires_at}.
 */
@Entity
@Table(name = "user_violations")
public class UserViolation extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private ViolationSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int points;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    protected UserViolation() {}

    public UserViolation(
            User user, ViolationSourceType sourceType, Long sourceId, int points, Instant expiresAt, User createdBy) {
        this.user = user;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.points = points;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ViolationSourceType getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public int getPoints() {
        return points;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }
}
