package com.localspot.controller;

import com.localspot.dto.response.AmenityResponse;
import com.localspot.dto.response.CategoryNode;
import com.localspot.service.CatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** openapi tag Catalog — công khai (khai báo trong {@code SecurityConfig}): form đề xuất, bộ lọc, trang chủ dùng chung. */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    public List<CategoryNode> categories() {
        return catalog.categoryTree();
    }

    @GetMapping("/amenities")
    public List<AmenityResponse> amenities() {
        return catalog.amenities();
    }
}
