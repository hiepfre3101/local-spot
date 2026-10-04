package com.localspot.controller;

import com.localspot.dto.request.PlaceCreateRequest;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.PlaceDetailResponse;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.repository.PlaceFilter;
import com.localspot.repository.PlaceSort;
import com.localspot.security.AuthenticatedUser;
import com.localspot.service.PlaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
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

/**
 * {@code /api/v1/places} — openapi tag Places (UC08–UC11). {@code GET} công khai (khai báo trong {@code SecurityConfig});
 * người đã đăng nhập gửi kèm token để nhận trường theo người xem ({@code myReviewId}, {@code claimable}).
 */
@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping
    public CursorPage<PlaceSummaryResponse> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> amenityIds,
            @RequestParam(required = false) @PositiveOrZero Integer priceMax,
            @RequestParam(required = false) @DecimalMin("1") @DecimalMax("5") BigDecimal minRating,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "SCORE") PlaceSort sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return placeService.list(
                new PlaceFilter(categoryId, amenityIds, priceMax, minRating, city), sort, cursor, limit);
    }

    /**
     * Đề xuất địa điểm (FR-15). multipart theo openapi: phần {@code place} là JSON; phần {@code photos} nhận ở checklist
     * E2 (upload ảnh qua hàng đợi) — giữ đúng định dạng request ngay từ giờ để frontend không phải đổi.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlaceDetailResponse> propose(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestPart("place") PlaceCreateRequest place) {
        PlaceDetailResponse created = placeService.propose(user.id(), place);
        return ResponseEntity.created(URI.create("/api/v1/places/" + created.slug()))
                .body(created);
    }

    @GetMapping("/{slug}")
    public PlaceDetailResponse detail(@PathVariable String slug, Authentication authentication) {
        return placeService.detail(slug, Viewers.from(authentication));
    }
}
