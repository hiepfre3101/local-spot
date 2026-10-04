package com.localspot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Phản hồi của chủ địa điểm: một phản hồi / đánh giá, sửa được (D6). */
@Entity
@Table(name = "owner_replies")
public class OwnerReply extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    private Review review;

    /** Chủ địa điểm tại thời điểm phản hồi (chủ có thể đổi về sau). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 2000)
    private String content;

    protected OwnerReply() {}

    public OwnerReply(Review review, User user, String content) {
        this.review = review;
        this.user = user;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public Review getReview() {
        return review;
    }

    public User getUser() {
        return user;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
