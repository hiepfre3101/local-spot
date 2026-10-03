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
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Yêu cầu xác nhận sở hữu (UC23, UC30). Minh chứng nằm ở bucket riêng tư, chỉ lưu khóa. */
@Entity
@Table(name = "place_claims")
public class PlaceClaim extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Column(name = "evidence_note", length = 1000)
    private String evidenceNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_keys", nullable = false)
    private List<String> evidenceKeys = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected PlaceClaim() {}

    public PlaceClaim(Place place, User user, String contactPhone, String evidenceNote, List<String> evidenceKeys) {
        this.place = place;
        this.user = user;
        this.contactPhone = contactPhone;
        this.evidenceNote = evidenceNote;
        this.evidenceKeys = new ArrayList<>(evidenceKeys);
        this.status = ClaimStatus.PENDING;
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

    public String getContactPhone() {
        return contactPhone;
    }

    public String getEvidenceNote() {
        return evidenceNote;
    }

    public List<String> getEvidenceKeys() {
        return evidenceKeys;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(User reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public int getVersion() {
        return version;
    }
}
