package com.localspot.service;

import com.localspot.config.CacheConfig;
import com.localspot.dto.response.HomeResponse;
import com.localspot.dto.response.HomeReviewResponse;
import com.localspot.dto.response.PlaceRef;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.entity.Review;
import com.localspot.mapper.ReviewMapper;
import com.localspot.repository.PlaceFilter;
import com.localspot.repository.PlaceSort;
import com.localspot.repository.ReviewRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trang chủ (FR-08, UC07) — một request cho cả trang: nổi bật theo điểm Bayesian, mới duyệt, cây danh mục, đánh giá nổi
 * bật. Dùng lại đúng truy vấn của {@code GET /places} (cùng điều kiện APPROVED, cùng thứ tự) — trang chủ không có luật
 * xếp hạng riêng.
 *
 * <p><b>Cache Redis</b> ({@link CacheConfig#HOME}, TTL 10 phút): response không phụ thuộc người xem nên mọi người dùng
 * chung một bản. Xóa sau commit khi địa điểm / điểm / review đang hiện / danh mục đổi ({@code HomeCacheInvalidator}); vote
 * và ảnh xử lý xong không xóa cache — chỉ trễ tối đa một TTL, chấp nhận cho trang giới thiệu.
 */
@Service
public class HomeService {

    static final int FEATURED_SIZE = 8;
    static final int LATEST_SIZE = 8;
    static final int EXCERPT_MAX = 180;

    /** Đánh giá nổi bật: từ 4 sao (trang chủ là lời mời khám phá) và ưu tiên 30 ngày gần đây cho trang chủ có sức sống. */
    static final int FEATURED_REVIEW_MIN_RATING = 4;

    static final Duration FEATURED_REVIEW_WINDOW = Duration.ofDays(30);

    private static final PlaceFilter NO_FILTER = new PlaceFilter(null, List.of(), null, null, null);

    private final PlaceService places;
    private final CatalogService catalog;
    private final BannerService banners;
    private final ReviewRepository reviews;
    private final ReviewMapper reviewMapper;
    private final Clock clock;

    public HomeService(
            PlaceService places,
            CatalogService catalog,
            BannerService banners,
            ReviewRepository reviews,
            ReviewMapper reviewMapper,
            Clock clock) {
        this.places = places;
        this.catalog = catalog;
        this.banners = banners;
        this.reviews = reviews;
        this.reviewMapper = reviewMapper;
        this.clock = clock;
    }

    @Cacheable(CacheConfig.HOME)
    @Transactional(readOnly = true)
    public HomeResponse home() {
        // Điểm 0 = chưa có review PUBLISHED ("Chưa có đánh giá") — không đưa lên mục nổi bật
        List<PlaceSummaryResponse> featured =
                places.list(NO_FILTER, PlaceSort.SCORE, null, FEATURED_SIZE).items().stream()
                        .filter(place -> place.reviewCount() > 0)
                        .toList();
        List<PlaceSummaryResponse> latest =
                places.list(NO_FILTER, PlaceSort.NEWEST, null, LATEST_SIZE).items();
        return new HomeResponse(featured, latest, catalog.categoryTree(), featuredReview(), banners.active());
    }

    /** Trong cửa sổ 30 ngày trước; chưa có review nào đủ điều kiện trong cửa sổ thì lấy mọi thời điểm. */
    private HomeReviewResponse featuredReview() {
        Instant since = clock.instant().minus(FEATURED_REVIEW_WINDOW);
        List<Review> recent = reviews.findMostHelpfulVisible(FEATURED_REVIEW_MIN_RATING, since, Limit.of(1));
        List<Review> picked = recent.isEmpty()
                ? reviews.findMostHelpfulVisible(FEATURED_REVIEW_MIN_RATING, Instant.EPOCH, Limit.of(1))
                : recent;
        return picked.isEmpty() ? null : toHomeReview(picked.getFirst());
    }

    private HomeReviewResponse toHomeReview(Review review) {
        return new HomeReviewResponse(
                review.getId(),
                excerpt(review.getContent()),
                review.getRating(),
                review.getHelpfulCount(),
                reviewMapper.toUserSummary(review.getUser()),
                new PlaceRef(
                        review.getPlace().getId(),
                        review.getPlace().getSlug(),
                        review.getPlace().getName()),
                review.getCreatedAt());
    }

    /**
     * Đoạn trích ≤ {@value #EXCERPT_MAX} ký tự (ReviewCard): gộp khoảng trắng / xuống dòng; dài hơn thì cắt ở ranh giới
     * từ gần nhất (không cắt giữa chữ tiếng Việt) và thêm "…". Đếm theo code point — không tách đôi cặp surrogate.
     */
    static String excerpt(String content) {
        String text = content.strip().replaceAll("\\s+", " ");
        if (text.codePointCount(0, text.length()) <= EXCERPT_MAX) {
            return text;
        }
        int end = text.offsetByCodePoints(0, EXCERPT_MAX - 1); // chừa chỗ cho "…"
        int space = text.lastIndexOf(' ', end);
        // Từ cuối dài bất thường (URL…) → cắt cứng thay vì bỏ gần hết đoạn
        int cut = space >= end / 2 ? space : end;
        return text.substring(0, cut).stripTrailing().replaceAll("[\\s,.;:!?-]+$", "") + "…";
    }
}
