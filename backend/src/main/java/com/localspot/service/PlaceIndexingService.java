package com.localspot.service;

import com.localspot.entity.Amenity;
import com.localspot.entity.Category;
import com.localspot.entity.Place;
import com.localspot.entity.PlaceStatus;
import com.localspot.entity.TaggableType;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.TaggableRepository;
import com.localspot.search.PlaceSearchDocument;
import com.localspot.search.PlaceSearchIndex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Đồng bộ địa điểm sang index tìm kiếm (UC08 "quy tắc nghiệp vụ") — gọi từ consumer {@code search.reindex}. Đọc trạng
 * thái <b>hiện tại</b> trong CSDL: địa điểm APPROVED → ghi (thêm / thay), còn lại (PENDING, REJECTED, HIDDEN, đã xóa mềm,
 * không tồn tại) → xóa khỏi index. Nhờ vậy message không cần nói "thêm" hay "xóa", và chạy lặp vẫn đúng.
 *
 * <p>Như consumer ảnh: chỉ mở transaction ngắn để đọc, không giữ kết nối CSDL trong lúc chờ Meilisearch.
 */
@Service
public class PlaceIndexingService {

    private final PlaceRepository places;
    private final TaggableRepository taggables;
    private final PlaceSearchIndex index;
    private final TransactionTemplate readOnlyTx;

    public PlaceIndexingService(
            PlaceRepository places, TaggableRepository taggables, PlaceSearchIndex index, TransactionTemplate tx) {
        this.places = places;
        this.taggables = taggables;
        this.index = index;
        this.readOnlyTx = new TransactionTemplate(tx.getTransactionManager());
        this.readOnlyTx.setReadOnly(true);
    }

    public void sync(List<Long> placeIds) {
        Set<Long> requested = new LinkedHashSet<>(placeIds);
        if (requested.isEmpty()) {
            return;
        }
        List<PlaceSearchDocument> documents = readOnlyTx.execute(status -> documentsOf(requested));
        Set<Long> toDelete = new LinkedHashSet<>(requested);
        documents.forEach(document -> toDelete.remove(document.id()));
        index.upsert(documents);
        index.delete(List.copyOf(toDelete));
    }

    private List<PlaceSearchDocument> documentsOf(Set<Long> placeIds) {
        List<Place> approved = places.findForSearchIndexByIdIn(placeIds).stream()
                .filter(place -> place.getStatus() == PlaceStatus.APPROVED)
                .toList();
        if (approved.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> tags = new HashMap<>();
        for (Object[] row : taggables.findTagNamesOf(
                TaggableType.PLACE, approved.stream().map(Place::getId).toList())) {
            tags.computeIfAbsent(((Number) row[0]).longValue(), id -> new ArrayList<>())
                    .add((String) row[1]);
        }
        return approved.stream()
                .map(place -> toDocument(place, tags.getOrDefault(place.getId(), List.of())))
                .toList();
    }

    static PlaceSearchDocument toDocument(Place place, List<String> tags) {
        Category category = place.getCategory();
        List<String> categoryNames = new ArrayList<>(List.of(category.getName()));
        List<Long> categoryIds = new ArrayList<>(List.of(category.getId()));
        if (category.getParent() != null) {
            categoryNames.add(category.getParent().getName());
            categoryIds.add(category.getParent().getId());
        }
        return new PlaceSearchDocument(
                place.getId(),
                place.getName(),
                tags,
                categoryNames,
                place.getAddress(),
                place.getCity(),
                place.getDescription(),
                categoryIds,
                place.getAmenities().stream().map(Amenity::getId).sorted().toList(),
                place.getPriceMin(),
                place.getBayesianScore(),
                place.getReviewCount());
    }
}
