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
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

/**
 * Check-in (U5). Lưu vị trí, độ chính xác GPS và khoảng cách đã kiểm tra để thống kê / giải trình; một check-in / địa
 * điểm / ngày ràng buộc ở CSDL.
 */
@Entity
@Table(name = "check_ins")
public class CheckIn extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false)
    private Point location;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "accuracy_m", nullable = false)
    private int accuracyM;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "distance_m", nullable = false)
    private int distanceM;

    /** Ngày theo giờ Việt Nam. */
    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    protected CheckIn() {}

    public CheckIn(User user, Place place, Point location, int accuracyM, int distanceM, LocalDate checkinDate) {
        this.user = user;
        this.place = place;
        this.location = location;
        this.accuracyM = accuracyM;
        this.distanceM = distanceM;
        this.checkinDate = checkinDate;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Place getPlace() {
        return place;
    }

    public Point getLocation() {
        return location;
    }

    public int getAccuracyM() {
        return accuracyM;
    }

    public int getDistanceM() {
        return distanceM;
    }

    public LocalDate getCheckinDate() {
        return checkinDate;
    }
}
