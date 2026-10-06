package com.localspot.controller;

import com.localspot.dto.request.PlaceUpdateRequest;
import com.localspot.dto.response.PhotoResponse;
import com.localspot.dto.response.PlaceDetailResponse;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.security.AuthenticatedUser;
import com.localspot.security.OwnershipPermissionEvaluator;
import com.localspot.security.Permissions;
import com.localspot.service.PlacePhotoService;
import com.localspot.service.PlaceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code /api/v1/owner/places} — chủ địa điểm (UC24, FR-32). Sửa địa điểm kiểm cả permission lẫn quyền sở hữu bằng
 * {@link OwnershipPermissionEvaluator} (403 nếu không phải chủ, 404 nếu không tồn tại).
 */
@RestController
@RequestMapping("/api/v1/owner/places")
public class OwnerPlaceController {

    private final PlaceService placeService;
    private final PlacePhotoService photoService;

    public OwnerPlaceController(PlaceService placeService, PlacePhotoService photoService) {
        this.placeService = placeService;
        this.photoService = photoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.PLACE_UPDATE_OWN + "')")
    public List<PlaceSummaryResponse> owned(@AuthenticationPrincipal AuthenticatedUser user) {
        return placeService.ownedBy(user.id());
    }

    @PatchMapping("/{placeId}")
    @PreAuthorize("hasPermission(#placeId, '" + OwnershipPermissionEvaluator.PLACE + "', '"
            + Permissions.PLACE_UPDATE_OWN + "')")
    public PlaceDetailResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long placeId,
            @Valid @RequestBody PlaceUpdateRequest request) {
        return placeService.updateByOwner(user.id(), placeId, request);
    }

    /** Chủ thêm ảnh (FR-32): 201 kèm các ảnh vừa nhận, trạng thái PROCESSING — xử lý nền qua queue (FR-16). */
    @PostMapping(path = "/{placeId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(#placeId, '" + OwnershipPermissionEvaluator.PLACE + "', '"
            + Permissions.PLACE_UPDATE_OWN + "')")
    public List<PhotoResponse> addPhotos(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long placeId,
            @RequestPart("photos") List<MultipartFile> photos) {
        return photoService.store(placeId, user.id(), photoService.inspect(photos));
    }

    @DeleteMapping("/{placeId}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission(#placeId, '" + OwnershipPermissionEvaluator.PLACE + "', '"
            + Permissions.PLACE_UPDATE_OWN + "')")
    public void deletePhoto(@PathVariable Long placeId, @PathVariable Long photoId) {
        photoService.delete(placeId, photoId);
    }
}
