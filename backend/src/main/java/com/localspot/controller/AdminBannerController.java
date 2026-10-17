package com.localspot.controller;

import com.localspot.dto.request.BannerUpdateRequest;
import com.localspot.dto.response.BannerResponse;
import com.localspot.entity.BannerSlot;
import com.localspot.security.AuthenticatedUser;
import com.localspot.security.Permissions;
import com.localspot.service.BannerService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** {@code /api/v1/admin/banners} — ảnh trang chủ (UC34, FR-43): ba ô cố định, quản trị viên thay ảnh / bật tắt / gỡ. */
@RestController
@RequestMapping("/api/v1/admin/banners")
public class AdminBannerController {

    private final BannerService banners;

    public AdminBannerController(BannerService banners) {
        this.banners = banners;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.BANNER_MANAGE + "')")
    public List<BannerResponse> list() {
        return banners.list();
    }

    /** Multipart: phần {@code image} (JPEG / PNG), {@code altText} tùy chọn — bỏ trống thì giữ mô tả cũ. */
    @PutMapping(path = "/{slot}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.BANNER_MANAGE + "')")
    public BannerResponse replaceImage(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable BannerSlot slot,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestParam(required = false) String altText) {
        return banners.replaceImage(admin.id(), slot, image, altText);
    }

    @PatchMapping("/{slot}")
    @PreAuthorize("hasAuthority('" + Permissions.BANNER_MANAGE + "')")
    public BannerResponse update(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable BannerSlot slot,
            @Valid @RequestBody BannerUpdateRequest request) {
        return banners.update(admin.id(), slot, request);
    }

    @DeleteMapping("/{slot}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.BANNER_MANAGE + "')")
    public void remove(@PathVariable BannerSlot slot) {
        banners.remove(slot);
    }
}
