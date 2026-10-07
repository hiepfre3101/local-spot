package com.localspot.service;

import com.localspot.dto.response.PhotoResponse;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.entity.Place;
import com.localspot.mapper.PlaceMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Thẻ địa điểm cho mọi danh sách (công khai, của tôi, của chủ, hàng chờ duyệt): mapper + ảnh bìa nạp một lần cho cả trang
 * — không truy vấn ảnh từng địa điểm (N+1).
 */
@Component
public class PlaceSummaries {

    private final PlaceMapper mapper;
    private final PlacePhotoService photos;

    public PlaceSummaries(PlaceMapper mapper, PlacePhotoService photos) {
        this.mapper = mapper;
        this.photos = photos;
    }

    public List<PlaceSummaryResponse> of(List<Place> places) {
        return of(places, Map.of());
    }

    /** Kèm {@code distanceM} theo id địa điểm (chỉ truy vấn không gian có — {@code /nearby}, {@code /duplicates}). */
    public List<PlaceSummaryResponse> of(List<Place> places, Map<Long, Integer> distances) {
        Map<Long, PhotoResponse> covers =
                photos.covers(places.stream().map(Place::getId).toList());
        return mapper.toSummaries(places).stream()
                .map(summary -> withExtras(summary, covers.get(summary.id()), distances.get(summary.id())))
                .toList();
    }

    /** Record bất biến → dựng lại với ảnh bìa và khoảng cách (mapper để trống hai trường này). */
    private static PlaceSummaryResponse withExtras(PlaceSummaryResponse s, PhotoResponse cover, Integer distanceM) {
        return new PlaceSummaryResponse(
                s.id(),
                s.slug(),
                s.name(),
                s.category(),
                s.address(),
                s.location(),
                cover,
                s.priceMin(),
                s.priceMax(),
                s.bayesianScore(),
                s.avgRating(),
                s.reviewCount(),
                s.status(),
                distanceM);
    }
}
