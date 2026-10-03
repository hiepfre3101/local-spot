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

/**
 * Bộ sưu tập địa điểm (bảng {@code collections}). Đặt tên {@code PlaceCollection} để không trùng
 * {@link java.util.Collection}. {@code shareSlug} chỉ có khi công khai.
 */
@Entity
@Table(name = "collections")
public class PlaceCollection extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "is_public", nullable = false)
    private boolean publicCollection;

    @Column(name = "share_slug", length = 40)
    private String shareSlug;

    protected PlaceCollection() {}

    public PlaceCollection(User user, String name) {
        this.user = user;
        this.name = name;
    }

    /** Công khai kèm slug chia sẻ; chuyển về riêng tư thì thu hồi slug để link cũ hết hiệu lực. */
    public void publish(String shareSlug) {
        this.publicCollection = true;
        this.shareSlug = shareSlug;
    }

    public void makePrivate() {
        this.publicCollection = false;
        this.shareSlug = null;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isPublicCollection() {
        return publicCollection;
    }

    public String getShareSlug() {
        return shareSlug;
    }
}
