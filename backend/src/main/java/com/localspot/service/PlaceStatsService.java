package com.localspot.service;

import com.localspot.dto.response.PlaceStatsResponse;
import com.localspot.entity.Place;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.StatsQueries;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thống kê địa điểm cho chủ (UC26, FR-34) — {@code /owner/places/{id}/stats}. Quyền (chủ hiện tại, 403 / 404) kiểm ở
 * {@code OwnershipPermissionEvaluator} trước khi vào đây. Địa điểm đang bị ẩn vẫn xem được: số liệu là của chủ.
 */
@Service
public class PlaceStatsService {

    /** Màn thống kê của chủ mặc định 30 ngày. */
    static final int DEFAULT_DAYS = 30;

    private final PlaceRepository places;
    private final PlaceService placeService;
    private final StatsQueries stats;
    private final Clock clock;

    public PlaceStatsService(PlaceRepository places, PlaceService placeService, StatsQueries stats, Clock clock) {
        this.places = places;
        this.placeService = placeService;
        this.stats = stats;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlaceStatsResponse stats(Long placeId, LocalDate from, LocalDate to) {
        StatsRange range = StatsRange.of(from, to, DEFAULT_DAYS, clock);
        Place place = places.findById(placeId).orElseThrow(PlaceService::placeNotFound);
        return new PlaceStatsResponse(
                range.from(),
                range.to(),
                range.series(stats.viewsByDay(placeId, range.from(), range.to())),
                range.series(stats.checkinsByDay(placeId, range.from(), range.to())),
                placeService.ratingDistribution(placeId),
                place.getBayesianScore(),
                place.getReviewCount());
    }
}
