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

/** Ảnh địa điểm. {@code storageKey} là khóa gốc trên S3/MinIO; URL các kích thước suy ra từ khóa. */
@Entity
@Table(name = "place_photos")
public class PlacePhoto extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhotoStatus status;

    @Column(name = "is_cover", nullable = false)
    private boolean cover;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private Integer width;

    private Integer height;

    protected PlacePhoto() {}

    public PlacePhoto(Place place, User uploadedBy, String storageKey) {
        this.place = place;
        this.uploadedBy = uploadedBy;
        this.storageKey = storageKey;
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

    public Place getPlace() {
        return place;
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

    public boolean isCover() {
        return cover;
    }

    public void setCover(boolean cover) {
        this.cover = cover;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }
}
