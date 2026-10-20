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

/**
 * Vote "hữu ích". Khóa chính (review_id, user_id) = {@code UNIQUE(review_id, user_id)} của plan §5. {@code counted}
 * chụp điều kiện trust ≥ 30 của người vote tại thời điểm vote (requirements §5.1) — không tính lại về sau.
 */
@Entity
@Table(name = "review_votes")
public class ReviewVote extends CreatedAtEntity {

    @EmbeddedId
    private Id id;

    @MapsId("reviewId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id")
    private Review review;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, updatable = false)
    private boolean counted;

    protected ReviewVote() {}

    public ReviewVote(Review review, User user, boolean counted) {
        this.id = new Id(review.getId(), user.getId());
        this.review = review;
        this.user = user;
        this.counted = counted;
    }

    public Id getId() {
        return id;
    }

    public Review getReview() {
        return review;
    }

    public User getUser() {
        return user;
    }

    public boolean isCounted() {
        return counted;
    }

    @Embeddable
    public record Id(
            @Column(name = "review_id") Long reviewId,
            @Column(name = "user_id") Long userId) implements Serializable {}
}
