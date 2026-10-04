package com.localspot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;

/** Gắn thẻ đa hình (D5): {@code taggableId} không có FK, toàn vẹn kiểm tra ở service. */
@Entity
@Table(name = "taggables")
public class Taggable {

    @EmbeddedId
    private Id id;

    @MapsId("tagId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    protected Taggable() {}

    public Taggable(Tag tag, TaggableType type, Long taggableId, User createdBy) {
        this.id = new Id(tag.getId(), type, taggableId);
        this.tag = tag;
        this.createdBy = createdBy;
    }

    public Id getId() {
        return id;
    }

    public Tag getTag() {
        return tag;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    @Embeddable
    public record Id(
            @Column(name = "tag_id") Long tagId,

            @Enumerated(EnumType.STRING) @Column(name = "taggable_type", length = 20)
            TaggableType taggableType,

            @Column(name = "taggable_id") Long taggableId)
            implements Serializable {}
}
