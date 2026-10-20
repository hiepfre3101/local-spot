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

/** Ảnh đính kèm đánh giá — cùng vòng đời xử lý nền như {@link PlacePhoto}. */
@Entity
@Table(name = "review_photos")
public class ReviewPhoto extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhotoStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private Integer width;

    private Integer height;

    protected ReviewPhoto() {}

    public ReviewPhoto(Review review, User uploadedBy, String storageKey, int sortOrder) {
        this.review = review;
        this.uploadedBy = uploadedBy;
        this.storageKey = storageKey;
        this.sortOrder = sortOrder;
        this.status = PhotoStatus.PROCESSING;
    }

    public void markReady(int width, int height) {
        this.status = PhotoStatus.READY;
        this.width = width;
        this.height = height;
    }

    public void markFailed() {
        this.status = PhotoStatus.FAILED;
    }

    public Long getId() {
        return id;
    }

    public Review getReview() {
        return review;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public PhotoStatus getStatus() {
        return status;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }
}
