package com.localspot.service;

import com.localspot.dto.response.PhotoResponse;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.entity.Place;
import com.localspot.entity.TaggableType;
import com.localspot.mapper.PlaceMapper;
import com.localspot.repository.TaggableRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Thẻ địa điểm cho mọi danh sách (công khai, của tôi, của chủ, hàng chờ duyệt): mapper + ảnh bìa + thẻ gắn nạp một lần
 * cho cả trang — không truy vấn ảnh / thẻ từng địa điểm (N+1).
 */
@Component
public class PlaceSummaries {

    private final PlaceMapper mapper;
    private final PlacePhotoService photos;
    private final TaggableRepository taggables;

    public PlaceSummaries(PlaceMapper mapper, PlacePhotoService photos, TaggableRepository taggables) {
        this.mapper = mapper;
        this.photos = photos;
        this.taggables = taggables;
    }

    public List<PlaceSummaryResponse> of(List<Place> places) {
        return of(places, Map.of());
    }

    /** Kèm {@code distanceM} theo id địa điểm (chỉ truy vấn không gian có — {@code /nearby}, {@code /duplicates}). */
    public List<PlaceSummaryResponse> of(List<Place> places, Map<Long, Integer> distances) {
        if (places.isEmpty()) {
            return List.of();
        }
        List<Long> ids = places.stream().map(Place::getId).toList();
        Map<Long, PhotoResponse> covers = photos.covers(ids);
        Map<Long, List<String>> tags = tagsOf(ids);
        return mapper.toSummaries(places).stream()
                .map(summary -> withExtras(
                        summary,
                        covers.get(summary.id()),
                        distances.get(summary.id()),
                        tags.getOrDefault(summary.id(), List.of())))
                .toList();
    }

    /** Một truy vấn cho cả trang (index {@code (taggable_type, taggable_id)}), giữ thứ tự chữ cái của truy vấn. */
    private Map<Long, List<String>> tagsOf(List<Long> placeIds) {
        Map<Long, List<String>> byPlace = new HashMap<>();
        for (Object[] row : taggables.findTagNamesOf(TaggableType.PLACE, placeIds)) {
            byPlace.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add((String) row[1]);
        }
        return byPlace;
    }

    /** Record bất biến → dựng lại với ảnh bìa, khoảng cách và thẻ (mapper để trống các trường này). */
    private static PlaceSummaryResponse withExtras(
            PlaceSummaryResponse s, PhotoResponse cover, Integer distanceM, List<String> tags) {
        return new PlaceSummaryResponse(
                s.id(),
                s.slug(),
                s.name(),
                s.category(),
                s.address(),
                s.city(),
                s.location(),
                cover,
                s.priceMin(),
                s.priceMax(),
                s.bayesianScore(),
                s.avgRating(),
                s.reviewCount(),
                s.status(),
                distanceM,
                tags);
    }
}
