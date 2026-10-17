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

/** Ảnh một ô trang chủ (FR-43, UC34). Một dòng / ô ({@code UNIQUE(slot)}); chưa có dòng = hình minh hoạ mặc định. */
@Entity
@Table(name = "home_banners")
public class HomeBanner extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private BannerSlot slot;

    /** Gốc khóa ảnh: {@code banners/{uuid}} — 3 bản theo {@code PhotoSize}. */
    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Column(name = "alt_text", nullable = false, length = 200)
    private String altText = "";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by", nullable = false)
    private User updatedBy;

    @Version
    @Column(nullable = false)
    private int version;

    protected HomeBanner() {}

    public HomeBanner(BannerSlot slot, String storageKey, User updatedBy) {
        this.slot = slot;
        this.storageKey = storageKey;
        this.updatedBy = updatedBy;
    }

    public Long getId() {
        return id;
    }

    public BannerSlot getSlot() {
        return slot;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(User updatedBy) {
        this.updatedBy = updatedBy;
    }

    public int getVersion() {
        return version;
    }
}
