package com.localspot.service;

import com.localspot.dto.response.DashboardResponse;
import com.localspot.dto.response.DashboardResponse.CityCount;
import com.localspot.dto.response.DashboardResponse.HotPlace;
import com.localspot.dto.response.DashboardResponse.PendingCounts;
import com.localspot.dto.response.DashboardResponse.Totals;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.entity.ModerationItemType;
import com.localspot.entity.Place;
import com.localspot.repository.ModerationQueueQueries;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.StatsQueries;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard quản trị (UC33, FR-41). Tính mỗi lần mở, không cache: chỉ quản trị viên xem, và số việc tồn cần đúng lúc
 * đó (vừa duyệt xong quay lại dashboard phải thấy giảm).
 */
@Service
public class AdminDashboardService {

    /** AdminDashboard mặc định "14 ngày qua". */
    static final int DEFAULT_DAYS = 14;

    static final int CITY_LIMIT = 5;
    static final int HOT_PLACE_LIMIT = 10;

    private final StatsQueries stats;
    private final ModerationQueueQueries queue;
    private final PlaceRepository places;
    private final PlaceSummaries summaries;
    private final Clock clock;

    public AdminDashboardService(
            StatsQueries stats,
            ModerationQueueQueries queue,
            PlaceRepository places,
            PlaceSummaries summaries,
            Clock clock) {
        this.stats = stats;
        this.queue = queue;
        this.places = places;
        this.summaries = summaries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(LocalDate from, LocalDate to) {
        StatsRange range = StatsRange.of(from, to, DEFAULT_DAYS, clock);
        Instant start = range.start();
        Instant end = range.end();
        return new DashboardResponse(
                range.from(),
                range.to(),
                clock.instant(),
                totals(range),
                totals(range.previous()),
                range.series(stats.newUsersByDay(start, end)),
                range.series(stats.reviewsByDay(start, end)),
                stats.visibleReviewsByCity(start, end, CITY_LIMIT).stream()
                        .map(row -> new CityCount(row.city(), row.count()))
                        .toList(),
                hotPlaces(start, end),
                pendingCounts());
    }

    private Totals totals(StatsRange range) {
        Instant start = range.start();
        Instant end = range.end();
        return new Totals(stats.countUsers(start, end), stats.countReviews(start, end), stats.countReports(start, end));
    }

    /** Giữ thứ tự xếp hạng của truy vấn; nạp thẻ địa điểm theo lô (danh mục + ảnh bìa + thẻ — không N+1). */
    private List<HotPlace> hotPlaces(Instant start, Instant end) {
        List<StatsQueries.PlaceCount> ranked = stats.hotPlaces(start, end, HOT_PLACE_LIMIT);
        if (ranked.isEmpty()) {
            return List.of();
        }
        Map<Long, Place> byId =
                places
                        .findWithCategoryByIdIn(ranked.stream()
                                .map(StatsQueries.PlaceCount::placeId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Place::getId, Function.identity()));
        List<Place> ordered = ranked.stream()
                .map(row -> byId.get(row.placeId()))
                .filter(Objects::nonNull)
                .toList();
        Map<Long, PlaceSummaryResponse> cards =
                summaries.of(ordered).stream().collect(Collectors.toMap(PlaceSummaryResponse::id, Function.identity()));
        List<HotPlace> hot = new ArrayList<>(ranked.size());
        for (StatsQueries.PlaceCount row : ranked) {
            PlaceSummaryResponse card = cards.get(row.placeId());
            if (card != null) {
                hot.add(new HotPlace(card, row.count()));
            }
        }
        return hot;
    }

    /** Cùng định nghĩa với hàng chờ gộp — số trên dashboard luôn khớp số mục khi bấm sang hàng chờ. */
    private PendingCounts pendingCounts() {
        Map<ModerationItemType, ModerationQueueQueries.Pending> pending =
                queue.pending(EnumSet.allOf(ModerationItemType.class));
        Instant oldest = pending.values().stream()
                .map(ModerationQueueQueries.Pending::oldestSubmittedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        return new PendingCounts(
                countOf(pending, ModerationItemType.PLACE),
                countOf(pending, ModerationItemType.REVIEW),
                countOf(pending, ModerationItemType.REPORT),
                countOf(pending, ModerationItemType.CLAIM),
                oldest);
    }

    private static long countOf(
            Map<ModerationItemType, ModerationQueueQueries.Pending> pending, ModerationItemType type) {
        ModerationQueueQueries.Pending p = pending.get(type);
        return p == null ? 0 : p.count();
    }
}
