package com.localspot.entity;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Nơi duy nhất tạo {@link Point} cho cột {@code POINT SRID 4326}. Quy ước JTS: x = kinh độ (lng), y = vĩ độ (lat);
 * hibernate-spatial tự đổi sang thứ tự trục (lat, lng) mà MySQL 8 dùng cho SRID 4326. Gom về một chỗ để không ai
 * truyền nhầm thứ tự — lỗi kiểu này không báo gì mà chỉ đặt quán ra giữa biển.
 */
public final class GeoPoints {

    public static final int WGS84 = 4326;

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), WGS84);

    private GeoPoints() {}

    public static Point of(double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Tọa độ ngoài phạm vi: lat=" + lat + ", lng=" + lng);
        }
        return FACTORY.createPoint(new Coordinate(lng, lat));
    }

    public static double lat(Point point) {
        return point.getY();
    }

    public static double lng(Point point) {
        return point.getX();
    }
}
