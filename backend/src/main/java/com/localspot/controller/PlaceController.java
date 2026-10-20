package com.localspot.controller;

import com.localspot.dto.request.PlaceCreateRequest;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.PlaceDetailResponse;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.repository.PlaceFilter;
import com.localspot.repository.PlaceSort;
import com.localspot.security.AuthenticatedUser;
import com.localspot.service.NearbyPlaceService;
import com.localspot.service.PlaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code /api/v1/places} — openapi tag Places (UC08–UC11). {@code GET} công khai (khai báo trong {@code SecurityConfig});
 * người đã đăng nhập gửi kèm token để nhận trường theo người xem ({@code myReviewId}, {@code claimable}).
 */
@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private static final String LAT_RANGE = "Vĩ độ phải từ -90 đến 90.";
    private static final String LNG_RANGE = "Kinh độ phải từ -180 đến 180.";
    private static final String RADIUS_RANGE = "Bán kính từ 100 m đến 20 km.";
    private static final String LIMIT_RANGE = "Số kết quả từ 1 đến 50.";

    private final PlaceService placeService;
    private final NearbyPlaceService nearbyService;

    public PlaceController(PlaceService placeService, NearbyPlaceService nearbyService) {
        this.placeService = placeService;
        this.nearbyService = nearbyService;
    }

    @GetMapping
    public CursorPage<PlaceSummaryResponse> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> amenityIds,
            @RequestParam(required = false) @PositiveOrZero(message = "Giá tối đa không được âm.") Integer priceMax,
            @RequestParam(required = false)
                    @DecimalMin(value = "1", message = "Điểm tối thiểu từ 1 đến 5.")
                    @DecimalMax(value = "5", message = "Điểm tối thiểu từ 1 đến 5.")
                    BigDecimal minRating,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "SCORE") PlaceSort sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return placeService.list(
                new PlaceFilter(categoryId, amenityIds, priceMax, minRating, city), sort, cursor, limit);
    }

    /**
     * Đề xuất địa điểm (FR-15). multipart theo openapi: phần {@code place} là JSON; phần {@code photos} (không bắt buộc,
     * ≤ 10 ảnh JPEG / PNG) được xử lý nền — response trả ảnh ở trạng thái PROCESSING.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlaceDetailResponse> propose(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestPart("place") PlaceCreateRequest place,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        PlaceDetailResponse created = placeService.propose(user.id(), place, photos);
        return ResponseEntity.created(URI.create("/api/v1/places/" + created.slug()))
                .body(created);
    }

    /** Tìm quanh vị trí (FR-11, UC09): mặc định 2 km, 100 m – 20 km (U8), gần nhất trước, kèm {@code distanceM}. */
    @GetMapping("/nearby")
    public List<PlaceSummaryResponse> nearby(
            @RequestParam @DecimalMin(value = "-90", message = LAT_RANGE) @DecimalMax(value = "90", message = LAT_RANGE)
                    double lat,
            @RequestParam
                    @DecimalMin(value = "-180", message = LNG_RANGE)
                    @DecimalMax(value = "180", message = LNG_RANGE)
                    double lng,
            @RequestParam(defaultValue = "2000")
                    @Min(value = 100, message = RADIUS_RANGE)
                    @Max(value = 20000, message = RADIUS_RANGE)
                    int radius,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = LIMIT_RANGE)
                    @Max(value = 50, message = LIMIT_RANGE)
                    int limit) {
        return nearbyService.nearby(lat, lng, radius, categoryId, limit);
    }

    /**
     * Nghi trùng trước khi đề xuất (U7): tên gần giống trong 50 m — chỉ cảnh báo. Cần đăng nhập (khai báo trong
     * {@code SecurityConfig}, trước luật công khai {@code /places/*}).
     */
    @GetMapping("/duplicates")
    public List<PlaceSummaryResponse> duplicates(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam
                    @NotBlank(message = "Tên không được để trống.")
                    @Size(max = 200, message = "Tên tối đa 200 ký tự.")
                    String name,
            @RequestParam @DecimalMin(value = "-90", message = LAT_RANGE) @DecimalMax(value = "90", message = LAT_RANGE)
                    double lat,
            @RequestParam
                    @DecimalMin(value = "-180", message = LNG_RANGE)
                    @DecimalMax(value = "180", message = LNG_RANGE)
                    double lng) {
        return nearbyService.duplicates(user.id(), name, lat, lng);
    }

    @GetMapping("/{slug}")
    public PlaceDetailResponse detail(@PathVariable String slug, Authentication authentication) {
        return placeService.detail(slug, Viewers.from(authentication));
    }
}
