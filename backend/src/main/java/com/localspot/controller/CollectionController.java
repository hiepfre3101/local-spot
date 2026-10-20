package com.localspot.controller;

import com.localspot.dto.request.CollectionPlaceRequest;
import com.localspot.dto.request.CollectionRequest;
import com.localspot.dto.response.CollectionDetailResponse;
import com.localspot.dto.response.CollectionSummaryResponse;
import com.localspot.security.AuthenticatedUser;
import com.localspot.service.CollectionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bộ sưu tập (UC18). Quyền chủ sở hữu kiểm trong service (không có permission RBAC — dữ liệu cá nhân, không có quyền vượt
 * cho nhân sự). {@code GET /me/collections} ở {@link MeController}.
 */
@RestController
@RequestMapping("/api/v1")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionDetailResponse create(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CollectionRequest request) {
        return collectionService.create(user.id(), request);
    }

    /** Công khai, hoặc của chính người xem (kèm token). */
    @GetMapping("/collections/{collectionId}")
    public CollectionDetailResponse get(@PathVariable Long collectionId, Authentication authentication) {
        return collectionService.get(collectionId, Viewers.from(authentication).userId());
    }

    @PatchMapping("/collections/{collectionId}")
    public CollectionDetailResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long collectionId,
            @Valid @RequestBody CollectionRequest request) {
        return collectionService.update(user.id(), collectionId, request);
    }

    @DeleteMapping("/collections/{collectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long collectionId) {
        collectionService.delete(user.id(), collectionId);
    }

    @PutMapping("/collections/{collectionId}/places/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addPlace(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long collectionId,
            @PathVariable Long placeId,
            @Valid @RequestBody(required = false) CollectionPlaceRequest request) {
        collectionService.addPlace(user.id(), collectionId, placeId, request);
    }

    @DeleteMapping("/collections/{collectionId}/places/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePlace(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long collectionId,
            @PathVariable Long placeId) {
        collectionService.removePlace(user.id(), collectionId, placeId);
    }

    @GetMapping("/shared/collections/{shareSlug}")
    public CollectionDetailResponse shared(@PathVariable String shareSlug) {
        return collectionService.shared(shareSlug);
    }

    /** Tab bộ sưu tập trên trang cá nhân (P06) — chỉ bộ công khai. */
    @GetMapping("/users/{userId}/collections")
    public List<CollectionSummaryResponse> publicOf(@PathVariable Long userId) {
        return collectionService.publicOf(userId);
    }
}
