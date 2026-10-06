package com.localspot.repository;

import com.localspot.entity.Place;
import com.localspot.entity.PlaceStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.HashMap;
import java.util.List;
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
}
