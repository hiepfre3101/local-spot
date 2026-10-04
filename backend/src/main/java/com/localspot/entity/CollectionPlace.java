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

/** Địa điểm trong bộ sưu tập; khóa chính tổ hợp (collection_id, place_id) theo plan §5. */
@Entity
@Table(name = "collection_place")
public class CollectionPlace {

    @EmbeddedId
    private Id id;

    @MapsId("collectionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id")
    private PlaceCollection collection;

    @MapsId("placeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(length = 500)
    private String note;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected CollectionPlace() {}

    public CollectionPlace(PlaceCollection collection, Place place, String note, Instant addedAt) {
        this.id = new Id(collection.getId(), place.getId());
        this.collection = collection;
        this.place = place;
        this.note = note;
        this.addedAt = addedAt;
    }

    public Id getId() {
        return id;
    }

    public PlaceCollection getCollection() {
        return collection;
    }

    public Place getPlace() {
        return place;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    @Embeddable
    public record Id(
            @Column(name = "collection_id") Long collectionId,
            @Column(name = "place_id") Long placeId) implements Serializable {}
}
