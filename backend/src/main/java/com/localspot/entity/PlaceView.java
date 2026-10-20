package com.localspot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * Lượt xem gộp theo ngày (giờ Việt Nam). Ghi bằng native upsert {@code ON DUPLICATE KEY UPDATE} để không mất lượt khi
 * nhiều request đồng thời; entity này chỉ để đọc thống kê.
 */
@Entity
@Table(name = "place_views")
public class PlaceView {

    @EmbeddedId
    private Id id;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    protected PlaceView() {}

    public Id getId() {
        return id;
    }

    public int getViewCount() {
        return viewCount;
    }

    @Embeddable
    public record Id(
            @Column(name = "place_id") Long placeId,
            @Column(name = "view_date") LocalDate viewDate) implements Serializable {}
}
