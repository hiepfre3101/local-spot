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

/** Quan hệ theo dõi giữa người dùng. Không tự theo dõi mình — CHECK ở CSDL. */
@Entity
@Table(name = "follows")
public class Follow extends CreatedAtEntity {

    @EmbeddedId
    private Id id;

    @MapsId("followerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id")
    private User follower;

    @MapsId("followingId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "following_id")
    private User following;

    protected Follow() {}

    public Follow(User follower, User following) {
        this.id = new Id(follower.getId(), following.getId());
        this.follower = follower;
        this.following = following;
    }

    public Id getId() {
        return id;
    }

    public User getFollower() {
        return follower;
    }

    public User getFollowing() {
        return following;
    }

    @Embeddable
    public record Id(
            @Column(name = "follower_id") Long followerId,
            @Column(name = "following_id") Long followingId) implements Serializable {}
}
