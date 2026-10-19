package com.localspot.controller;

import com.localspot.dto.response.AmenityResponse;
import com.localspot.dto.response.CategoryNode;
import com.localspot.dto.response.CityResponse;
import com.localspot.dto.response.HomeResponse;
import com.localspot.service.CatalogService;
import com.localspot.service.HomeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** openapi tag Catalog — công khai (khai báo trong {@code SecurityConfig}): form đề xuất, bộ lọc, trang chủ dùng chung. */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalog;
    private final HomeService home;

    public CatalogController(CatalogService catalog, HomeService home) {
        this.catalog = catalog;
        this.home = home;
    }

    /** Trang chủ (FR-08) — openapi đặt ở tag Catalog. */
    @GetMapping("/home")
    public HomeResponse home() {
        return home.home();
    }

    @GetMapping("/categories")
    public List<CategoryNode> categories() {
        return catalog.categoryTree();
    }

    @GetMapping("/amenities")
    public List<AmenityResponse> amenities() {
        return catalog.amenities();
    }

    /** Thành phố có địa điểm — bộ lọc khu vực của bảng xếp hạng (P08, thêm 2026-10-19). */
    @GetMapping("/cities")
    public List<CityResponse> cities() {
        return catalog.cities();
    }
}
