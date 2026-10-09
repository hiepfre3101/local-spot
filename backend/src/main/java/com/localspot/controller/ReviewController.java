package com.localspot.controller;

import com.localspot.dto.request.ReviewCreateRequest;
import com.localspot.dto.request.ReviewUpdateRequest;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.PhotoResponse;
import com.localspot.dto.response.ReviewResponse;
import com.localspot.security.AuthenticatedUser;
import com.localspot.security.OwnershipPermissionEvaluator;
import com.localspot.security.Permissions;
import com.localspot.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * openapi tag Reviews (UC12–UC14). {@code GET} công khai (khai báo trong {@code SecurityConfig}); người đã đăng nhập gửi
 * kèm token để nhận {@code votedByMe}.
 */
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private static final String RATING_RANGE = "Số sao từ 1 đến 5.";

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/places/{placeId}/reviews")
    public CursorPage<ReviewResponse> listForPlace(
            @PathVariable Long placeId,
            @RequestParam(defaultValue = "NEWEST") ReviewService.Sort sort,
            @RequestParam(required = false)
                    @Min(value = 1, message = RATING_RANGE)
                    @Max(value = 5, message = RATING_RANGE)
                    Integer rating,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            Authentication authentication) {
        return reviewService.listForPlace(
                placeId,
                sort,
                rating,
                cursor,
                limit,
                Viewers.from(authentication).userId());
    }

    /**
     * Viết review (UC12) — multipart theo openapi (S2): phần {@code review} là JSON, phần {@code photos} (không bắt buộc,
     * ≤ 10 ảnh JPEG / PNG) xử lý nền. 201 kèm {@code status}: PUBLISHED hoặc PENDING (chờ kiểm duyệt).
     */
    @PostMapping(path = "/places/{placeId}/reviews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.REVIEW_CREATE + "')")
    public ResponseEntity<ReviewResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long placeId,
            @Valid @RequestPart("review") ReviewCreateRequest review,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
            HttpServletRequest http) {
        return ResponseEntity.status(201)
                .body(reviewService.create(user.id(), placeId, review, photos, ClientInfos.from(http)));
    }

    /** Thêm ảnh vào review của mình (chốt 2026-10-09) — 201 kèm ảnh vừa nhận, PROCESSING. */
    @PostMapping(path = "/reviews/{reviewId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasPermission(#reviewId, '" + OwnershipPermissionEvaluator.REVIEW + "', '"
            + Permissions.REVIEW_UPDATE_OWN + "')")
    public ResponseEntity<List<PhotoResponse>> addPhotos(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long reviewId,
            @RequestPart("photos") List<MultipartFile> photos) {
        return ResponseEntity.status(201).body(reviewService.addPhotos(user.id(), reviewId, photos));
    }

    @DeleteMapping("/reviews/{reviewId}/photos/{photoId}")
    @PreAuthorize("hasPermission(#reviewId, '" + OwnershipPermissionEvaluator.REVIEW + "', '"
            + Permissions.REVIEW_UPDATE_OWN + "')")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long reviewId, @PathVariable Long photoId) {
        reviewService.deletePhoto(reviewId, photoId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/reviews/{reviewId}")
    @PreAuthorize("hasPermission(#reviewId, '" + OwnershipPermissionEvaluator.REVIEW + "', '"
            + Permissions.REVIEW_UPDATE_OWN + "')")
    public ReviewResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest request) {
        return reviewService.update(user.id(), reviewId, request);
    }

    @DeleteMapping("/reviews/{reviewId}")
    @PreAuthorize("hasPermission(#reviewId, '" + OwnershipPermissionEvaluator.REVIEW + "', '"
            + Permissions.REVIEW_DELETE_OWN + "')")
    public ResponseEntity<Void> delete(@PathVariable Long reviewId) {
        reviewService.delete(reviewId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}/reviews")
    public CursorPage<ReviewResponse> publishedBy(
            @PathVariable Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            Authentication authentication) {
        return reviewService.publishedBy(
                userId, cursor, limit, Viewers.from(authentication).userId());
    }
}
