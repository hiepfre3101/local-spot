package com.localspot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/** Huy hiệu đã trao cho người dùng. */
@Entity
@Table(name = "user_badges")
public class UserBadge {

    @EmbeddedId
    private Id id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("badgeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "badge_id")
    private Badge badge;

    @Column(name = "awarded_at", nullable = false, updatable = false)
    private Instant awardedAt;

    protected UserBadge() {}

    public UserBadge(User user, Badge badge, Instant awardedAt) {
        this.id = new Id(user.getId(), badge.getId());
        this.user = user;
        this.badge = badge;
        this.awardedAt = awardedAt;
    }

    public Id getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Badge getBadge() {
        return badge;
    }

    public Instant getAwardedAt() {
        return awardedAt;
    }

    @Embeddable
    public record Id(
            @Column(name = "user_id") Long userId,
            @Column(name = "badge_id") Long badgeId) implements Serializable {}
}
