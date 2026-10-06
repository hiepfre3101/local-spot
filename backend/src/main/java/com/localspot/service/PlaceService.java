package com.localspot.service;

import com.localspot.dto.request.OpeningHourRequest;
import com.localspot.dto.request.PlaceCreateRequest;
import com.localspot.dto.request.PlaceUpdateRequest;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.PhotoResponse;
import com.localspot.dto.response.PlaceDetailResponse;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.entity.Amenity;
import com.localspot.entity.Category;
import com.localspot.entity.ClaimStatus;
import com.localspot.entity.GeoPoints;
import com.localspot.entity.OpeningHour;
import com.localspot.entity.Place;
import com.localspot.entity.PlaceStatus;
import com.localspot.entity.Tag;
import com.localspot.entity.Taggable;
import com.localspot.entity.TaggableType;
import com.localspot.entity.User;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.mapper.PlaceMapper;
import com.localspot.mapper.PlaceMapper.DetailExtras;
import com.localspot.repository.AmenityRepository;
import com.localspot.repository.CategoryRepository;
import com.localspot.repository.PlaceClaimRepository;
import com.localspot.repository.PlaceFilter;
import com.localspot.repository.PlaceKeyset;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.PlaceSort;
import com.localspot.repository.PlaceViewRepository;
import com.localspot.repository.ReviewRepository;
import com.localspot.repository.TagRepository;
import com.localspot.repository.TaggableRepository;
import com.localspot.repository.UserRepository;
import com.localspot.service.ImageInspector.Inspected;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Địa điểm: đề xuất (UC11, FR-15), xem chi tiết (FR-13), danh sách + lọc (FR-10), chủ cập nhật (UC24, FR-32). Duyệt /
 * từ chối nằm ở {@link PlaceModerationService}.
 *
 * <p><b>Ai xem được địa điểm chưa duyệt</b> (PENDING / REJECTED / HIDDEN): người đề xuất, chủ địa điểm và kiểm duyệt
 * viên ({@code place:approve}); người khác nhận 404 như thể không tồn tại — không lộ đề xuất đang chờ của người khác.
 */
@Service
public class PlaceService {

    /** Ngày của lượt xem tính theo giờ Việt Nam (database.md §3.2 {@code place_views}). */
    static final ZoneId VIEW_DATE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final int SLUG_BASE_MAX = 200;
    private static final int TAG_SLUG_MAX = 60;
    private static final String FALLBACK_SLUG = "dia-diem";

    private final PlaceRepository places;
    private final CategoryRepository categories;
    private final AmenityRepository amenities;
    private final UserRepository users;
    private final TagRepository tags;
    private final TaggableRepository taggables;
    private final ReviewRepository reviews;
    private final PlaceClaimRepository claims;
    private final PlaceViewRepository views;
    private final PlaceMapper mapper;
    private final PlacePhotoService photos;
    private final PlaceSummaries summaries;
    private final Clock clock;

    public PlaceService(
            PlaceRepository places,
            CategoryRepository categories,
            AmenityRepository amenities,
            UserRepository users,
            TagRepository tags,
            TaggableRepository taggables,
            ReviewRepository reviews,
            PlaceClaimRepository claims,
            PlaceViewRepository views,
            PlaceMapper mapper,
            PlacePhotoService photos,
            PlaceSummaries summaries,
            Clock clock) {
        this.places = places;
        this.categories = categories;
        this.amenities = amenities;
        this.users = users;
        this.tags = tags;
        this.taggables = taggables;
        this.reviews = reviews;
        this.claims = claims;
        this.views = views;
        this.mapper = mapper;
        this.photos = photos;
        this.summaries = summaries;
        this.clock = clock;
    }

    /**
     * Đề xuất địa điểm mới → PENDING, chờ kiểm duyệt (UC27), kèm tối đa 10 ảnh (xử lý nền — E2). Kiểm tra nghi trùng
     * (U7) là endpoint riêng {@code /places/duplicates} gọi trước khi gửi — chỉ cảnh báo, không chặn ở đây.
     */
    @Transactional
    public PlaceDetailResponse propose(Long userId, PlaceCreateRequest request, List<MultipartFile> photoFiles) {
        List<Inspected> images = photos.inspect(photoFiles); // ảnh hỏng → 422 trước khi ghi gì
        Category category = categories
                .findById(request.categoryId())
                .orElseThrow(() ->
                        ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "categoryId", "Danh mục không tồn tại."));
        checkPriceRange(request.priceMin(), request.priceMax());
        Set<Amenity> amenitySet = resolveAmenities(request.amenityIds());
        List<OpeningHour> hours = toOpeningHours(request.openingHours());
        Map<String, String> tagsBySlug = normalizeTags(request.tags());

        String name = request.name().strip();
        String city = request.city().strip();
        User proposer = users.getReferenceById(userId);
        Place place = new Place(
                category,
                proposer,
                name,
                uniqueSlug(name + " " + city),
                request.address().strip(),
                city,
                GeoPoints.of(request.location().lat(), request.location().lng()),
                PlaceStatus.PENDING);
        place.setDescription(blankToNull(request.description()));
        place.setPriceMin(request.priceMin());
        place.setPriceMax(request.priceMax());
        place.setPhone(blankToNull(request.phone()));
        place.setWebsite(blankToNull(request.website()));
        place.getAmenities().addAll(amenitySet);
        hours.forEach(place::addOpeningHour);
        places.save(place); // IDENTITY → INSERT ngay, có id để gắn thẻ

        attachTags(place.getId(), proposer, tagsBySlug);
        List<PhotoResponse> stored = photos.store(place.getId(), userId, images);
        return mapper.toDetail(
                place,
                new DetailExtras(
                        taggables.findTagNames(TaggableType.PLACE, place.getId()),
                        emptyDistribution(),
                        false,
                        null,
                        true,
                        null, // ảnh bìa chỉ có khi đã xử lý xong
                        stored));
    }

    /**
     * Chi tiết theo slug. Địa điểm đã duyệt được +1 lượt xem trong ngày (FR-13, phục vụ thống kê FR-34) — trừ khi người
     * xem chính là chủ, để chủ mở trang của mình không làm phồng số liệu.
     */
    @Transactional
    public PlaceDetailResponse detail(String slug, Viewer viewer) {
        Place place = places.findDetailBySlug(slug).orElseThrow(PlaceService::placeNotFound);
        Long ownerId = place.getOwner() == null ? null : place.getOwner().getId();
        Long proposerId = place.getCreatedBy().getId();
        boolean approved = place.getStatus() == PlaceStatus.APPROVED;
        if (!approved && !viewer.moderator() && !viewer.is(proposerId) && !viewer.is(ownerId)) {
            throw placeNotFound();
        }
        if (approved && !viewer.is(ownerId)) {
            views.increment(place.getId(), LocalDate.now(clock.withZone(VIEW_DATE_ZONE)));
        }
        return toDetail(place, viewer);
    }

    /** Chủ địa điểm sửa thông tin (quyền sở hữu đã kiểm ở {@code @PreAuthorize} của controller). */
    @Transactional
    public PlaceDetailResponse updateByOwner(Long ownerId, Long placeId, PlaceUpdateRequest request) {
        Place place = places.findDetailById(placeId).orElseThrow(PlaceService::placeNotFound);
        checkVersion(place, request.version());

        if (request.address() != null) {
            if (request.address().isBlank()) {
                throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "address", "Địa chỉ không được để trống.");
            }
            place.setAddress(request.address().strip());
        }
        Integer priceMin = request.priceMin() != null ? request.priceMin() : place.getPriceMin();
        Integer priceMax = request.priceMax() != null ? request.priceMax() : place.getPriceMax();
        checkPriceRange(priceMin, priceMax);
        place.setPriceMin(priceMin);
        place.setPriceMax(priceMax);
        if (request.description() != null) {
            place.setDescription(blankToNull(request.description()));
        }
        if (request.phone() != null) {
            place.setPhone(blankToNull(request.phone()));
        }
        if (request.website() != null) {
            place.setWebsite(blankToNull(request.website()));
        }
        if (request.amenityIds() != null) {
            Set<Amenity> amenitySet = resolveAmenities(request.amenityIds());
            place.getAmenities().clear();
            place.getAmenities().addAll(amenitySet);
        }
        if (request.openingHours() != null) {
            List<OpeningHour> hours = toOpeningHours(request.openingHours());
            place.getOpeningHours().clear(); // orphanRemoval xóa khung giờ cũ
            hours.forEach(place::addOpeningHour);
        }
        places.flush(); // UPDATE ngay → version mới nằm trong response cho lần sửa kế tiếp
        return toDetail(place, new Viewer(ownerId, false));
    }

    /** Danh sách địa điểm đã duyệt + lọc + sắp xếp (FR-10), phân trang keyset theo thứ tự đã chọn. */
    @Transactional(readOnly = true)
    public CursorPage<PlaceSummaryResponse> list(PlaceFilter filter, PlaceSort sort, String cursor, Integer limit) {
        int pageSize = KeysetCursor.limit(limit);
        PlaceKeyset after = PlaceListCursor.decode(cursor, sort);
        List<Place> rows = places.searchApproved(filter, sort, after, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<Place> page = hasMore ? rows.subList(0, pageSize) : rows;
        String next = hasMore ? PlaceListCursor.encode(keysetOf(page.getLast(), sort)) : null;
        return new CursorPage<>(summaries.of(page), next);
    }

    /** {@code GET /me/places}: địa điểm tôi đã đề xuất, mọi trạng thái (UC11 — "người đề xuất xem được trong trang cá nhân"). */
    @Transactional(readOnly = true)
    public CursorPage<PlaceSummaryResponse> proposedBy(Long userId, String cursor, Integer limit) {
        int pageSize = KeysetCursor.limit(limit);
        List<Place> rows = places.findCreatedBy(userId, KeysetCursor.decode(cursor), Limit.of(pageSize + 1));
        return KeysetPages.byId(rows, pageSize, Place::getId, summaries::of);
    }

    @Transactional(readOnly = true)
    public List<PlaceSummaryResponse> ownedBy(Long ownerId) {
        return summaries.of(places.findOwnedBy(ownerId));
    }

    // ─── Chi tiết ────────────────────────────────────────────────────────────

    private PlaceDetailResponse toDetail(Place place, Viewer viewer) {
        Long proposerId = place.getCreatedBy().getId();
        Long ownerId = place.getOwner() == null ? null : place.getOwner().getId();
        boolean seesUnreadyPhotos = viewer.moderator() || viewer.is(proposerId) || viewer.is(ownerId);
        boolean claimable = place.getStatus() == PlaceStatus.APPROVED
                && place.getOwner() == null
                && (viewer.userId() == null
                        || !claims.existsByPlaceIdAndUserIdAndStatus(
                                place.getId(), viewer.userId(), ClaimStatus.PENDING));
        Long myReviewId = viewer.userId() == null
                ? null
                : reviews.findIdIncludingDeleted(place.getId(), viewer.userId()).orElse(null);
        return mapper.toDetail(
                place,
                new DetailExtras(
                        taggables.findTagNames(TaggableType.PLACE, place.getId()),
                        ratingDistribution(place.getId()),
                        claimable,
                        myReviewId,
                        viewer.moderator() || viewer.is(proposerId),
                        photos.cover(place.getId()),
                        photos.gallery(place.getId(), seesUnreadyPhotos)));
    }

    /** Đủ 5 mức sao (mức không có review = 0) để frontend vẽ biểu đồ không phải tự điền chỗ trống. */
    private Map<Integer, Long> ratingDistribution(Long placeId) {
        Map<Integer, Long> distribution = emptyDistribution();
        for (Object[] row : reviews.countPublishedByRating(placeId)) {
            distribution.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return distribution;
    }

    private static Map<Integer, Long> emptyDistribution() {
        Map<Integer, Long> distribution = new TreeMap<>();
        for (int star = 1; star <= 5; star++) {
            distribution.put(star, 0L);
        }
        return distribution;
    }

    private static PlaceKeyset keysetOf(Place place, PlaceSort sort) {
        BigDecimal value = switch (sort) {
            case SCORE -> place.getBayesianScore();
            case MOST_REVIEWED -> BigDecimal.valueOf(place.getReviewCount());
            case NEWEST -> null;
        };
        return new PlaceKeyset(sort, value, place.getId());
    }

    // ─── Kiểm tra & chuẩn hóa dữ liệu vào ───────────────────────────────────

    private static void checkPriceRange(Integer priceMin, Integer priceMax) {
        if (priceMin != null && priceMax != null && priceMin > priceMax) {
            throw ApiException.fieldError(
                    ErrorCode.VALIDATION_FAILED, "priceMax", "Giá tối đa phải lớn hơn hoặc bằng giá tối thiểu.");
        }
    }

    static void checkVersion(Place place, Integer expected) {
        if (expected != null && expected != place.getVersion()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "Địa điểm vừa được cập nhật bởi người khác. Hãy tải lại rồi thử lại.");
        }
    }

    /** Id tiện ích phải tồn tại hết — id lạ bị báo lỗi thay vì âm thầm bỏ qua. */
    private Set<Amenity> resolveAmenities(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<Long> distinct = new LinkedHashSet<>(ids);
        List<Amenity> found = amenities.findAllById(distinct);
        if (found.size() != distinct.size()) {
            throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "amenityIds", "Có tiện ích không tồn tại.");
        }
        return new HashSet<>(found);
    }

    /** Định dạng {@code HH:mm} đã kiểm ở DTO; ở đây chặn giờ mở = giờ đóng (không rõ là đóng hay mở 24 giờ). */
    private static List<OpeningHour> toOpeningHours(List<OpeningHourRequest> requested) {
        if (requested == null) {
            return List.of();
        }
        List<OpeningHour> hours = new ArrayList<>(requested.size());
        for (int i = 0; i < requested.size(); i++) {
            OpeningHourRequest r = requested.get(i);
            LocalTime open = LocalTime.parse(r.openTime());
            LocalTime close = LocalTime.parse(r.closeTime());
            if (open.equals(close)) {
                throw ApiException.fieldError(
                        ErrorCode.VALIDATION_FAILED,
                        "openingHours[" + i + "].closeTime",
                        "Giờ đóng cửa phải khác giờ mở cửa (mở cả ngày: 00:00–23:59).");
            }
            hours.add(new OpeningHour(DayOfWeek.of(r.dayOfWeek()), open, close));
        }
        return hours;
    }

    /** slug → tên hiển thị; thẻ trùng slug ("View đẹp" / "view dep") gộp làm một, giữ cách viết đầu tiên. */
    private static Map<String, String> normalizeTags(List<String> requested) {
        Map<String, String> bySlug = new LinkedHashMap<>();
        if (requested == null) {
            return bySlug;
        }
        for (String raw : requested) {
            String name = raw.strip().replaceAll("\\s+", " ");
            String slug = Slugs.slugify(name, TAG_SLUG_MAX);
            if (slug.isEmpty()) {
                throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "tags", "Thẻ phải có chữ hoặc số: " + raw);
            }
            bySlug.putIfAbsent(slug, name);
        }
        return bySlug;
    }

    private void attachTags(Long placeId, User createdBy, Map<String, String> tagsBySlug) {
        if (tagsBySlug.isEmpty()) {
            return;
        }
        tagsBySlug.forEach((slug, name) -> tags.insertIfAbsent(name, slug));
        for (Tag tag : tags.findBySlugIn(tagsBySlug.keySet())) {
            taggables.save(new Taggable(tag, TaggableType.PLACE, placeId, createdBy));
        }
    }

    /**
     * Slug từ tên + thành phố ("Phở Thìn" ở Hà Nội → {@code pho-thin-ha-noi}): thân thiện SEO và ít trùng hơn chỉ dùng
     * tên. Trùng → thêm hậu tố {@code -2}, {@code -3}… Hai đề xuất cùng tên đồng thời vẫn có thể chọn cùng slug — UNIQUE
     * ở CSDL chặn request sau (409 {@code DUPLICATE_RESOURCE}, gửi lại là được); hiếm nên không khóa bảng.
     */
    private String uniqueSlug(String source) {
        String base = Slugs.slugify(source, SLUG_BASE_MAX);
        if (base.isEmpty()) {
            base = FALLBACK_SLUG;
        }
        Set<String> taken = new HashSet<>(places.findSlugsLike(base));
        if (!taken.contains(base)) {
            return base;
        }
        int suffix = 2;
        while (taken.contains(base + "-" + suffix)) {
            suffix++;
        }
        return base + "-" + suffix;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static ApiException placeNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PLACE_NOT_FOUND, "Không tìm thấy địa điểm.");
    }
}
