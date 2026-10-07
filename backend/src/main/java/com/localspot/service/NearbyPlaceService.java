package com.localspot.service;

import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.entity.GeoPoints;
import com.localspot.entity.Place;
import com.localspot.repository.PlaceDistance;
import com.localspot.repository.PlaceRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Truy vấn theo vị trí: tìm quanh đây (FR-11, UC09) và cảnh báo nghi trùng khi đề xuất (U7). Truy vấn không gian trả id +
 * khoảng cách (spatial index), rồi nạp địa điểm kèm danh mục một lần — giữ thứ tự gần nhất trước.
 */
@Service
public class NearbyPlaceService {

    /** U7: "trong 50 m". */
    static final int DUPLICATE_RADIUS_M = 50;

    /** Số ứng viên tối đa trong 50 m (khu ẩm thực, chợ — vẫn chỉ vài chục) trước khi so tên. */
    private static final int DUPLICATE_CANDIDATES = 50;

    private final PlaceRepository places;
    private final PlaceSummaries summaries;

    public NearbyPlaceService(PlaceRepository places, PlaceSummaries summaries) {
        this.places = places;
        this.summaries = summaries;
    }

    /** Địa điểm đã duyệt trong bán kính (giới hạn 100 m – 20 km kiểm ở controller — U8), gần nhất trước. */
    @Transactional(readOnly = true)
    public List<PlaceSummaryResponse> nearby(double lat, double lng, int radiusM, Long categoryId, int limit) {
        return toSummaries(places.findApprovedWithin(lat, lng, radiusM, categoryId, limit), place -> true);
    }

    /**
     * Địa điểm có tên gần giống trong 50 m (U7) — chỉ cảnh báo, không chặn đề xuất. Gồm địa điểm đã duyệt và đề xuất
     * đang chờ <b>của chính người hỏi</b> (chốt 2026-10-06): đề xuất chờ của người khác vẫn riêng tư như ở E1, kiểm duyệt
     * viên bắt trùng giữa các người dùng ở hàng chờ.
     */
    @Transactional(readOnly = true)
    public List<PlaceSummaryResponse> duplicates(long userId, String name, double lat, double lng) {
        List<PlaceDistance> candidates =
                places.findDuplicateCandidates(lat, lng, DUPLICATE_RADIUS_M, userId, DUPLICATE_CANDIDATES);
        return toSummaries(candidates, place -> PlaceNames.similar(name, place.getName()));
    }

    /**
     * Nghi trùng của một đề xuất trong hàng chờ kiểm duyệt: cùng luật tên gần giống trong 50 m, nhưng xét cả đề xuất chờ
     * của <b>người khác</b> — chỗ duy nhất hai đề xuất trùng của hai người dùng gặp nhau trước khi được duyệt (người đề
     * xuất không thấy đề xuất chờ của nhau).
     */
    @Transactional(readOnly = true)
    public List<PlaceSummaryResponse> possibleDuplicatesOf(Place place) {
        double lat = GeoPoints.lat(place.getLocation());
        double lng = GeoPoints.lng(place.getLocation());
        List<PlaceDistance> candidates = places.findModerationDuplicateCandidates(
                lat, lng, DUPLICATE_RADIUS_M, place.getId(), DUPLICATE_CANDIDATES);
        return toSummaries(candidates, other -> PlaceNames.similar(place.getName(), other.getName()));
    }

    private List<PlaceSummaryResponse> toSummaries(List<PlaceDistance> hits, Predicate<Place> keep) {
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> distances =
                hits.stream().collect(Collectors.toMap(PlaceDistance::placeId, PlaceDistance::distanceM));
        Map<Long, Place> byId = places.findWithCategoryByIdIn(distances.keySet()).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        List<Place> ordered = hits.stream()
                .map(hit -> byId.get(hit.placeId()))
                .filter(place -> place != null && keep.test(place))
                .sorted(Comparator.comparing((Place p) -> distances.get(p.getId()))
                        .thenComparing(Place::getId))
                .toList();
        return summaries.of(ordered, distances);
    }
}
