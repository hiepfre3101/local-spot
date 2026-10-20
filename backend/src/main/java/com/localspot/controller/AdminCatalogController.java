package com.localspot.controller;

import com.localspot.dto.request.AmenityRequest;
import com.localspot.dto.request.CategoryRequest;
import com.localspot.dto.response.AmenityResponse;
import com.localspot.dto.response.CategoryNode;
import com.localspot.security.Permissions;
import com.localspot.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/admin/categories}, {@code /admin/amenities} — quản trị viên quản lý danh mục & tiện ích (UC32, FR-39). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {

    private final CatalogService catalog;

    public AdminCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CATEGORY_MANAGE + "')")
    public CategoryNode createCategory(@Valid @RequestBody CategoryRequest request) {
        return catalog.createCategory(request);
    }

    @PutMapping("/categories/{categoryId}")
    @PreAuthorize("hasAuthority('" + Permissions.CATEGORY_MANAGE + "')")
    public CategoryNode updateCategory(@PathVariable Long categoryId, @Valid @RequestBody CategoryRequest request) {
        return catalog.updateCategory(categoryId, request);
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.CATEGORY_MANAGE + "')")
    public void deleteCategory(@PathVariable Long categoryId) {
        catalog.deleteCategory(categoryId);
    }

    @PostMapping("/amenities")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.AMENITY_MANAGE + "')")
    public AmenityResponse createAmenity(@Valid @RequestBody AmenityRequest request) {
        return catalog.createAmenity(request);
    }

    @PutMapping("/amenities/{amenityId}")
    @PreAuthorize("hasAuthority('" + Permissions.AMENITY_MANAGE + "')")
    public AmenityResponse updateAmenity(@PathVariable Long amenityId, @Valid @RequestBody AmenityRequest request) {
        return catalog.updateAmenity(amenityId, request);
    }

    @DeleteMapping("/amenities/{amenityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.AMENITY_MANAGE + "')")
    public void deleteAmenity(@PathVariable Long amenityId) {
        catalog.deleteAmenity(amenityId);
    }
}
