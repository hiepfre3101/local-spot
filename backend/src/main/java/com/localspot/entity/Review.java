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
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Đánh giá. {@code UNIQUE(place_id, user_id)} tính cả bản đã xóa mềm (D2). Chỉ {@code PUBLISHED} được tính vào điểm
 * xếp hạng; sửa bài đã đăng khi trust &lt; 30 đưa bài về {@code PENDING} (O2).
 */
@Entity
@Table(name = "reviews")
@SQLRestriction("deleted_at IS NULL")
public class Review extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false, updatable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "visited_at", nullable = false)
    private LocalDate visitedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    /** IPv4 /24 hoặc IPv6 /64 — khóa của luật cảnh báo IP (U3). */
    @Column(name = "ip_prefix", nullable = false, length = 45)
    private String ipPrefix;

    @Column(name = "ip_flagged", nullable = false)
    private boolean ipFlagged;

    /** Tổng vote hiển thị; cập nhật nguyên tử bằng UPDATE ± 1 ở repository, không qua entity. */
    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderated_by")
    private User moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Review() {}

    public Review(
            Place place,
            User user,
            int rating,
            String content,
            LocalDate visitedAt,
            ReviewStatus status,
            String ipAddress,
            String ipPrefix) {
        this.place = place;
        this.user = user;
        this.rating = rating;
        this.content = content;
        this.visitedAt = visitedAt;
        this.status = status;
        this.ipAddress = ipAddress;
        this.ipPrefix = ipPrefix;
    }

    public Long getId() {
        return id;
    }

    public Place getPlace() {
        return place;
    }

    public User getUser() {
        return user;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDate getVisitedAt() {
        return visitedAt;
    }

    public void setVisitedAt(LocalDate visitedAt) {
        this.visitedAt = visitedAt;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getIpPrefix() {
        return ipPrefix;
    }

    public boolean isIpFlagged() {
        return ipFlagged;
    }

    public void setIpFlagged(boolean ipFlagged) {
        this.ipFlagged = ipFlagged;
    }

    public int getHelpfulCount() {
        return helpfulCount;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public User getModeratedBy() {
        return moderatedBy;
    }

    public void setModeratedBy(User moderatedBy) {
        this.moderatedBy = moderatedBy;
    }

    public Instant getModeratedAt() {
        return moderatedAt;
    }

    public void setModeratedAt(Instant moderatedAt) {
        this.moderatedAt = moderatedAt;
    }

    public int getVersion() {
        return version;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
