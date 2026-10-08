package com.localspot.service;

import com.localspot.audit.AuditedAction;
import com.localspot.config.CacheConfig;
import com.localspot.dto.request.AmenityRequest;
import com.localspot.dto.request.CategoryRequest;
import com.localspot.dto.response.AmenityResponse;
import com.localspot.dto.response.CategoryNode;
import com.localspot.entity.Amenity;
import com.localspot.entity.Category;
import com.localspot.event.CatalogChangedEvent;
import com.localspot.event.PlaceIndexChangedEvent;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.AmenityRepository;
import com.localspot.repository.CategoryRepository;
import com.localspot.repository.PlaceRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh mục & tiện ích (FR-39, UC32): đọc công khai có cache Redis; quản trị viên tạo / sửa / xóa, ghi nhật ký (FR-42).
 *
 * <p><b>Cây tối đa 2 cấp</b> (chốt 2026-10-06): danh mục con chỉ gắn vào danh mục gốc; danh mục đang có con không được
 * chuyển thành con. Bộ lọc "danh mục gồm cả danh mục con" của {@code GET /places} dựa vào giới hạn này. <b>Tiện ích dùng
 * chung</b> cho mọi danh mục (O7, chốt 2026-10-06).
 *
 * <p><b>Xóa chỉ khi không còn dùng</b> (openapi): không xóa kéo theo địa điểm / danh mục con — kể cả địa điểm đã xóa mềm.
 */
@Service
public class CatalogService {

    private final CategoryRepository categories;
    private final AmenityRepository amenities;
    private final PlaceRepository places;
    private final ApplicationEventPublisher events;

    public CatalogService(
            CategoryRepository categories,
            AmenityRepository amenities,
            PlaceRepository places,
            ApplicationEventPublisher events) {
        this.categories = categories;
        this.amenities = amenities;
        this.places = places;
        this.events = events;
    }

    // ─── Đọc (công khai) ─────────────────────────────────────────────────────

    @Cacheable(CacheConfig.CATEGORIES)
    @Transactional(readOnly = true)
    public List<CategoryNode> categoryTree() {
        List<Category> all = categories.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, List<CategoryNode>> childrenByParent = new LinkedHashMap<>();
        for (Category c : all) {
            if (c.getParent() != null) {
                childrenByParent
                        .computeIfAbsent(c.getParent().getId(), id -> new ArrayList<>())
                        .add(node(c, List.of()));
            }
        }
        return all.stream()
                .filter(c -> c.getParent() == null)
                .map(c -> node(c, childrenByParent.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @Cacheable(CacheConfig.AMENITIES)
    @Transactional(readOnly = true)
    public List<AmenityResponse> amenities() {
        return amenities.findAllByOrderByIdAsc().stream()
                .map(CatalogService::toResponse)
                .toList();
    }

    // ─── Danh mục (quản trị) ────────────────────────────────────────────────

    @Transactional
    @AuditedAction(
            action = "CATEGORY_CREATE",
            targetType = "CATEGORY",
            targetId = "#result.id()",
            metadata = "{slug: #result.slug()}")
    public CategoryNode createCategory(CategoryRequest request) {
        if (categories.existsBySlug(request.slug())) {
            throw slugTaken();
        }
        Category parent = resolveParent(request.parentId(), null);
        Category category = new Category(parent, request.name().strip(), request.slug(), sortOrderOf(request));
        category.setIcon(blankToNull(request.icon()));
        categories.save(category);
        categoriesChanged();
        return node(category, List.of());
    }

    @Transactional
    @AuditedAction(
            action = "CATEGORY_UPDATE",
            targetType = "CATEGORY",
            targetId = "#categoryId",
            metadata = "{slug: #result.slug()}")
    public CategoryNode updateCategory(Long categoryId, CategoryRequest request) {
        Category category = categories.findById(categoryId).orElseThrow(CatalogService::categoryNotFound);
        if (categories.existsBySlugAndIdNot(request.slug(), categoryId)) {
            throw slugTaken();
        }
        Category parent = resolveParent(request.parentId(), category);
        boolean searchFieldsChanged = !category.getName().equals(request.name().strip())
                || !Objects.equals(idOf(category.getParent()), idOf(parent));
        category.setParent(parent);
        category.setName(request.name().strip());
        category.setSlug(request.slug());
        category.setIcon(blankToNull(request.icon()));
        category.setSortOrder(sortOrderOf(request));
        categoriesChanged();
        if (searchFieldsChanged) {
            // Tên danh mục (và danh mục cha) nằm trong tài liệu tìm kiếm của địa điểm thuộc nó và thuộc danh mục con
            events.publishEvent(new PlaceIndexChangedEvent(places.findApprovedIdsInCategoryTree(categoryId)));
        }
        List<CategoryNode> children = categories.findByParentIdOrderBySortOrderAscIdAsc(categoryId).stream()
                .map(child -> node(child, List.of()))
                .toList();
        return node(category, children);
    }

    @Transactional
    @AuditedAction(action = "CATEGORY_DELETE", targetType = "CATEGORY", targetId = "#categoryId")
    public void deleteCategory(Long categoryId) {
        Category category = categories.findById(categoryId).orElseThrow(CatalogService::categoryNotFound);
        if (categories.existsByParentId(categoryId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.CATEGORY_IN_USE,
                    "Danh mục còn danh mục con — chuyển hoặc xóa chúng trước.");
        }
        if (categories.countPlacesIncludingDeleted(categoryId) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ErrorCode.CATEGORY_IN_USE, "Danh mục đang có địa điểm, không xóa được.");
        }
        categories.delete(category);
        categoriesChanged();
    }

    /**
     * Cha phải tồn tại và là danh mục gốc; danh mục đang có con không được làm con (cây giữ ≤ 2 cấp); không tự làm cha
     * của chính mình.
     */
    private Category resolveParent(Long parentId, Category self) {
        if (parentId == null) {
            return null;
        }
        if (self != null && parentId.equals(self.getId())) {
            throw depthError("Danh mục không thể là cha của chính nó.");
        }
        Category parent = categories
                .findById(parentId)
                .orElseThrow(() -> ApiException.fieldError(
                        ErrorCode.VALIDATION_FAILED, "parentId", "Danh mục cha không tồn tại."));
        if (parent.getParent() != null) {
            throw depthError("Chỉ gắn được vào danh mục gốc (tối đa 2 cấp).");
        }
        if (self != null && categories.existsByParentId(self.getId())) {
            throw depthError("Danh mục đang có danh mục con nên phải là danh mục gốc (tối đa 2 cấp).");
        }
        return parent;
    }

    // ─── Tiện ích (quản trị) ─────────────────────────────────────────────────

    @Transactional
    @AuditedAction(
            action = "AMENITY_CREATE",
            targetType = "AMENITY",
            targetId = "#result.id()",
            metadata = "{slug: #result.slug()}")
    public AmenityResponse createAmenity(AmenityRequest request) {
        if (amenities.existsBySlug(request.slug())) {
            throw slugTaken();
        }
        Amenity amenity = new Amenity(request.name().strip(), request.slug());
        amenity.setIcon(blankToNull(request.icon()));
        amenitiesChanged();
        return toResponse(amenities.save(amenity));
    }

    @Transactional
    @AuditedAction(
            action = "AMENITY_UPDATE",
            targetType = "AMENITY",
            targetId = "#amenityId",
            metadata = "{slug: #result.slug()}")
    public AmenityResponse updateAmenity(Long amenityId, AmenityRequest request) {
        Amenity amenity = amenities.findById(amenityId).orElseThrow(CatalogService::amenityNotFound);
        if (amenities.existsBySlugAndIdNot(request.slug(), amenityId)) {
            throw slugTaken();
        }
        amenity.setName(request.name().strip());
        amenity.setSlug(request.slug());
        amenity.setIcon(blankToNull(request.icon()));
        amenitiesChanged();
        return toResponse(amenity);
    }

    @Transactional
    @AuditedAction(action = "AMENITY_DELETE", targetType = "AMENITY", targetId = "#amenityId")
    public void deleteAmenity(Long amenityId) {
        Amenity amenity = amenities.findById(amenityId).orElseThrow(CatalogService::amenityNotFound);
        if (amenities.countPlacesUsing(amenityId) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ErrorCode.AMENITY_IN_USE, "Tiện ích đang gắn với địa điểm, không xóa được.");
        }
        amenities.delete(amenity);
        amenitiesChanged();
    }

    // ─── Tiện ích nội bộ ─────────────────────────────────────────────────────

    /** Cache xóa sau commit ({@code CatalogCacheInvalidator}) — rollback thì cache giữ nguyên, vẫn đúng. */
    private static Long idOf(Category category) {
        return category == null ? null : category.getId();
    }

    private void categoriesChanged() {
        events.publishEvent(new CatalogChangedEvent(CacheConfig.CATEGORIES));
    }

    private void amenitiesChanged() {
        events.publishEvent(new CatalogChangedEvent(CacheConfig.AMENITIES));
    }

    private static CategoryNode node(Category c, List<CategoryNode> children) {
        return new CategoryNode(c.getId(), c.getName(), c.getSlug(), c.getIcon(), children);
    }

    private static AmenityResponse toResponse(Amenity a) {
        return new AmenityResponse(a.getId(), a.getName(), a.getSlug(), a.getIcon());
    }

    private static int sortOrderOf(CategoryRequest request) {
        return request.sortOrder() == null ? 0 : request.sortOrder();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ApiException slugTaken() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ErrorCode.SLUG_TAKEN,
                "Slug đã được dùng.",
                List.of(new ApiException.FieldError("slug", "Slug đã được dùng.")));
    }

    private static ApiException depthError(String message) {
        return ApiException.fieldError(ErrorCode.CATEGORY_DEPTH_EXCEEDED, "parentId", message);
    }

    private static ApiException categoryNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.CATEGORY_NOT_FOUND, "Không tìm thấy danh mục.");
    }

    private static ApiException amenityNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.AMENITY_NOT_FOUND, "Không tìm thấy tiện ích.");
    }
}
