package com.localspot.repository;

import com.localspot.entity.Place;
import com.localspot.entity.PlaceStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dựng JPQL theo bộ lọc. Chuỗi truy vấn chỉ ghép từ các mảnh cố định trong lớp này — giá trị người dùng luôn đi qua
 * tham số bind, không nối vào chuỗi (không SQL injection). Dùng JPQL thay vì Criteria API cho dễ đọc / đối chiếu với
 * index: {@code (category_id, status, bayesian_score)} phục vụ lọc danh mục + sắp theo điểm.
 */
class PlaceSearchRepositoryImpl implements PlaceSearchRepository {

    private final EntityManager entityManager;

    PlaceSearchRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<Place> searchApproved(PlaceFilter filter, PlaceSort sort, PlaceKeyset after, int limit) {
        StringBuilder jpql =
                new StringBuilder("SELECT p FROM Place p JOIN FETCH p.category c WHERE p.status = :status");
        Map<String, Object> params = new HashMap<>();
        params.put("status", PlaceStatus.APPROVED);

        if (filter.categoryId() != null) {
            jpql.append(" AND (c.id = :categoryId OR c.parent.id = :categoryId)");
            params.put("categoryId", filter.categoryId());
        }
        if (!filter.amenityIds().isEmpty()) {
            List<Long> amenityIds = filter.amenityIds().stream().distinct().toList();
            jpql.append(" AND p.id IN (SELECT pa.id FROM Place pa JOIN pa.amenities a WHERE a.id IN :amenityIds"
                    + " GROUP BY pa.id HAVING COUNT(a.id) = :amenityCount)");
            params.put("amenityIds", amenityIds);
            params.put("amenityCount", (long) amenityIds.size());
        }
        if (filter.priceMax() != null) {
            jpql.append(" AND p.priceMin <= :priceMax");
            params.put("priceMax", filter.priceMax());
        }
        if (filter.minRating() != null) {
            jpql.append(" AND p.bayesianScore >= :minRating");
            params.put("minRating", filter.minRating());
        }
        if (filter.city() != null && !filter.city().isBlank()) {
            jpql.append(" AND p.city = :city");
            params.put("city", filter.city().strip());
        }

        String sortColumn = switch (sort) {
            case SCORE -> "p.bayesianScore";
            case MOST_REVIEWED -> "p.reviewCount";
            case NEWEST -> null;
        };
        if (after != null) {
            if (sortColumn == null) {
                jpql.append(" AND p.id < :afterId");
            } else {
                jpql.append(" AND (" + sortColumn + " < :afterValue OR (" + sortColumn
                        + " = :afterValue AND p.id < :afterId))");
                params.put(
                        "afterValue",
                        sort == PlaceSort.MOST_REVIEWED ? after.value().intValueExact() : after.value());
            }
            params.put("afterId", after.id());
        }
        jpql.append(sortColumn == null ? " ORDER BY p.id DESC" : " ORDER BY " + sortColumn + " DESC, p.id DESC");

        TypedQuery<Place> query = entityManager.createQuery(jpql.toString(), Place.class);
        params.forEach(query::setParameter);
        return query.setMaxResults(limit).getResultList();
    }

    // ─── Truy vấn không gian (FR-11, U7) ────────────────────────────────────

    /** Mét trên một độ vĩ (xấp xỉ, đủ cho hộp lọc thô — khoảng cách thật tính bằng ST_Distance_Sphere). */
    private static final double METERS_PER_DEGREE_LAT = 111_320;

    /**
     * Hai bước (openapi {@code /places/nearby}): {@code MBRContains} với hộp bao quanh vòng tròn — dùng spatial index
     * {@code sx_places_location} — rồi lọc chính xác bằng {@code ST_Distance_Sphere}.
     *
     * <p>{@code FORCE INDEX}: với bảng nhỏ (vài trăm dòng) optimizer MySQL chọn quét toàn bảng dù index lọc được (đo
     * {@code EXPLAIN} trên dữ liệu seed: {@code type = ALL}); NFR-03 yêu cầu truy vấn bán kính luôn đi qua spatial
     * index, và với bảng lớn dần index luôn là lựa chọn đúng. Tọa độ SRID 4326 trong MySQL theo thứ tự trục (lat, lng).
     */
    static final String WITHIN_SQL = """
            SELECT p.id, ST_Distance_Sphere(p.location, ST_PointFromText(:center, 4326)) AS distance
            FROM places p FORCE INDEX (sx_places_location)
            JOIN categories c ON c.id = p.category_id
            WHERE p.deleted_at IS NULL
              AND MBRContains(ST_GeomFromText(:box, 4326), p.location)
              AND {condition}
            HAVING distance <= :radius
            ORDER BY distance, p.id
            LIMIT :limit
            """;

    @Override
    public List<PlaceDistance> findApprovedWithin(double lat, double lng, int radiusM, Long categoryId, int limit) {
        String condition = "p.status = 'APPROVED'"
                + (categoryId == null ? "" : " AND (p.category_id = :categoryId OR c.parent_id = :categoryId)");
        Query query = spatialQuery(condition, lat, lng, radiusM, limit);
        if (categoryId != null) {
            query.setParameter("categoryId", categoryId);
        }
        return toDistances(query.getResultList());
    }

    @Override
    public List<PlaceDistance> findDuplicateCandidates(
            double lat, double lng, int radiusM, long proposerId, int limit) {
        Query query = spatialQuery(
                "(p.status = 'APPROVED' OR (p.status = 'PENDING' AND p.created_by = :proposerId))",
                lat,
                lng,
                radiusM,
                limit);
        query.setParameter("proposerId", proposerId);
        return toDistances(query.getResultList());
    }

    @Override
    public List<PlaceDistance> findModerationDuplicateCandidates(
            double lat, double lng, int radiusM, long excludePlaceId, int limit) {
        Query query = spatialQuery(
                "p.status IN ('APPROVED', 'PENDING') AND p.id <> :excludePlaceId", lat, lng, radiusM, limit);
        query.setParameter("excludePlaceId", excludePlaceId);
        return toDistances(query.getResultList());
    }

    private Query spatialQuery(String condition, double lat, double lng, int radiusM, int limit) {
        return entityManager
                .createNativeQuery(WITHIN_SQL.replace("{condition}", condition))
                .setParameter("center", "POINT(%s %s)".formatted(coord(lat), coord(lng)))
                .setParameter("box", boundingBox(lat, lng, radiusM))
                .setParameter("radius", radiusM)
                .setParameter("limit", limit);
    }

    /**
     * Hộp (lat, lng) bao vòng tròn bán kính {@code radiusM}, nới thêm 1 % + 10 m để sai số của phép xấp xỉ độ ↔ mét
     * không làm rơi điểm nằm sát mép vòng tròn (hộp chỉ lọc thô, rộng hơn một chút không sai kết quả).
     */
    static String boundingBox(double lat, double lng, int radiusM) {
        double padded = radiusM * 1.01 + 10;
        double dLat = padded / METERS_PER_DEGREE_LAT;
        double cosLat = Math.max(Math.cos(Math.toRadians(lat)), 0.01); // tránh chia 0 sát cực
        double dLng = padded / (METERS_PER_DEGREE_LAT * cosLat);
        String south = coord(Math.max(lat - dLat, -90));
        String north = coord(Math.min(lat + dLat, 90));
        String west = coord(Math.max(lng - dLng, -180));
        String east = coord(Math.min(lng + dLng, 180));
        return "POLYGON((%s %s, %s %s, %s %s, %s %s, %s %s))"
                .formatted(south, west, north, west, north, east, south, east, south, west);
    }

    private static String coord(double value) {
        return String.format(Locale.ROOT, "%.7f", value);
    }

    private static List<PlaceDistance> toDistances(List<?> rows) {
        return rows.stream()
                .map(row -> (Object[]) row)
                .map(row -> new PlaceDistance(
                        ((Number) row[0]).longValue(), (int) Math.round(((Number) row[1]).doubleValue())))
                .toList();
    }
}
