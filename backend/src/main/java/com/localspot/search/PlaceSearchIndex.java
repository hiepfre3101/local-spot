package com.localspot.search;

import com.localspot.config.SearchProperties;
import com.localspot.repository.PlaceFilter;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.model.Pagination;
import com.meilisearch.sdk.model.SearchResult;
import com.meilisearch.sdk.model.Settings;
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
import com.meilisearch.sdk.model.TypoTolerance;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Index địa điểm trên Meilisearch (FR-09) — lớp duy nhất biết tới Meilisearch, như {@code ObjectStorage} với kho ảnh.
 * Mọi lỗi (không kết nối được, API báo lỗi, tác vụ thất bại) ném {@link MeilisearchException}.
 *
 * <p>Tiếng Việt: Meilisearch tự bỏ dấu và đổi {@code đ → d} khi lập chỉ mục lẫn khi tìm ("pho" ra "Phở", "dong do" ra
 * "Đông Đô" — đã đo trên v1.54), nên không cần cột không dấu riêng. Phần còn thiếu được cấu hình ở đây: synonym
 * ({@code search/synonyms.json} — "cafe" ↔ "cà phê"), ngưỡng lỗi chính tả cho âm tiết ngắn, chiến lược bỏ từ.
 */
@Component
public final class PlaceSearchIndex {

    /** Thứ tự = mức ưu tiên khi xếp hạng (luật {@code attributeRank}): khớp ở tên hơn khớp ở mô tả. */
    static final String[] SEARCHABLE = {"name", "tags", "categoryNames", "address", "city", "description"};

    /** {@code id} để xóa theo bộ lọc ({@code id IN [...]}) — cách xóa theo lô cũ của client đã deprecated. */
    static final String[] FILTERABLE = {"id", "categoryIds", "amenityIds", "priceMin", "bayesianScore"};

    static final String[] SORTABLE = {"bayesianScore", "reviewCount", "id"};

    /**
     * Luật mặc định của Meilisearch v1.54, thêm {@code bayesianScore:desc} ở cuối: kết quả liên quan ngang nhau thì địa
     * điểm được đánh giá tốt hơn (Bayesian — plan §5) đứng trước. Đặt cuối để điểm không lấn át độ liên quan.
     */
    static final String[] RANKING_RULES = {
        "words", "typo", "proximity", "attributeRank", "sort", "wordPosition", "exactness", "bayesianScore:desc"
    };

    /** Cửa sổ kết quả tối đa (mặc định của Meilisearch) — {@code offset + limit} vượt quá thì hết trang. */
    public static final int MAX_TOTAL_HITS = 1000;

    private static final String SYNONYMS = "search/synonyms.json";
    private static final int TASK_TIMEOUT_MS = 30_000;
    private static final int TASK_POLL_MS = 50;

    private final Client client;
    private final SearchProperties properties;
    private final JsonMapper json;
    private final Map<String, String[]> synonyms;
    private volatile boolean settingsApplied;

    public PlaceSearchIndex(Client client, SearchProperties properties, JsonMapper json) {
        this.client = client;
        this.properties = properties;
        this.json = json;
        this.synonyms = loadSynonyms(json);
    }

    /**
     * Áp cấu hình index (tạo index nếu chưa có). Gọi khi khởi động; Meilisearch lúc đó sập thì lần ghi đầu tiên gọi lại.
     * Cập nhật cấu hình là idempotent — Meilisearch chỉ lập chỉ mục lại khi cấu hình thật sự đổi.
     */
    public synchronized void applySettings() {
        if (settingsApplied) {
            return;
        }
        Settings settings = new Settings()
                .setSearchableAttributes(SEARCHABLE)
                .setFilterableAttributes(FILTERABLE)
                .setSortableAttributes(SORTABLE)
                .setRankingRules(RANKING_RULES)
                .setSynonyms(new HashMap<>(synonyms))
                .setTypoTolerance(new TypoTolerance()
                        .setEnabled(true) // trường boolean nguyên thủy: không đặt sẽ gửi false = tắt hẳn
                        .setMinWordSizeForTypos(new HashMap<>(Map.of(
                                "oneTypo", properties.oneTypoMinWordSize(),
                                "twoTypos", properties.twoTypoMinWordSize()))))
                .setPagination(pagination());
        await(index().updateSettings(settings));
        settingsApplied = true;
    }

    /** Thêm hoặc thay toàn bộ tài liệu theo {@code id}; chờ Meilisearch xử lý xong để lỗi được retry (NFR-13). */
    public void upsert(List<PlaceSearchDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        applySettings();
        await(index().addDocuments(json.writeValueAsString(documents), "id"));
    }

    public void delete(List<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return;
        }
        applySettings();
        String ids = String.join(", ", placeIds.stream().map(String::valueOf).toList());
        await(index().deleteDocumentsByFilter("id IN [" + ids + "]"));
    }

    /** Id địa điểm theo thứ tự kết quả. Không gọi {@link #applySettings()} — đường đọc phải nhanh, lỗi thì fallback. */
    public List<Long> search(PlaceSearchQuery query) {
        SearchRequest request = new SearchRequest(query.q())
                .setFilter(filterOf(query.filter()))
                .setSort(sortOf(query.sort()))
                .setOffset(query.offset())
                .setLimit(query.limit())
                .setAttributesToRetrieve(new String[] {"id"})
                .setMatchingStrategy(properties.matchingStrategy());
        SearchResult result = (SearchResult) index().search(request); // offset / limit → SearchResult
        List<Long> ids = new ArrayList<>(result.getHits().size());
        for (Map<String, Object> hit : result.getHits()) {
            ids.add(((Number) hit.get("id")).longValue()); // Gson đọc số JSON thành Double
        }
        return ids;
    }

    /** Biểu thức lọc Meilisearch; các phần tử của mảng được AND với nhau. Giá trị đều là số — không có chuỗi người dùng. */
    static String[] filterOf(PlaceFilter filter) {
        List<String> clauses = new ArrayList<>();
        if (filter.categoryId() != null) {
            clauses.add("categoryIds = " + filter.categoryId());
        }
        for (Long amenityId : new LinkedHashSet<>(filter.amenityIds())) {
            clauses.add("amenityIds = " + amenityId); // đủ tất cả tiện ích (AND) như GET /places
        }
        if (filter.priceMax() != null) {
            clauses.add("priceMin <= " + filter.priceMax());
        }
        if (filter.minRating() != null) {
            clauses.add("bayesianScore >= " + filter.minRating().toPlainString());
        }
        return clauses.toArray(String[]::new);
    }

    static String[] sortOf(SearchSort sort) {
        return switch (sort) {
            case RELEVANCE -> null; // chỉ theo luật xếp hạng
            case SCORE -> new String[] {"bayesianScore:desc", "id:desc"};
            case NEWEST -> new String[] {"id:desc"};
            case MOST_REVIEWED -> new String[] {"reviewCount:desc", "id:desc"};
        };
    }

    private static Pagination pagination() {
        Pagination pagination = new Pagination();
        pagination.setMaxTotalHits(MAX_TOTAL_HITS);
        return pagination;
    }

    private Index index() {
        return client.index(properties.index());
    }

    /** Ghi trên Meilisearch là tác vụ bất đồng bộ — chờ xong và coi tác vụ thất bại là lỗi. */
    private void await(TaskInfo info) {
        client.index(properties.index()).waitForTask(info.getTaskUid(), TASK_TIMEOUT_MS, TASK_POLL_MS);
        Task task = client.getTask(info.getTaskUid());
        if (task.getStatus() != TaskStatus.SUCCEEDED) {
            String reason = task.getError() == null
                    ? task.getStatus().toString()
                    : task.getError().getMessage();
            throw new MeilisearchException("Tác vụ Meilisearch " + task.getType() + " thất bại: " + reason);
        }
    }

    /**
     * {@code search/synonyms.json} là danh sách <b>nhóm</b> từ tương đương — dễ duy trì hơn dạng ánh xạ của Meilisearch.
     * Mỗi từ trong nhóm nhận các từ còn lại làm synonym (hai chiều); một từ ở nhiều nhóm thì gộp.
     */
    static Map<String, String[]> loadSynonyms(JsonMapper json) {
        List<List<String>> groups;
        try (InputStream in = new ClassPathResource(SYNONYMS).getInputStream()) {
            groups = json.readValue(in, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được " + SYNONYMS, e);
        }
        Map<String, Set<String>> merged = new HashMap<>();
        for (List<String> group : groups) {
            for (String term : group) {
                Set<String> others = merged.computeIfAbsent(term.strip(), key -> new LinkedHashSet<>());
                group.stream()
                        .map(String::strip)
                        .filter(other -> !other.equals(term.strip()))
                        .forEach(others::add);
            }
        }
        Map<String, String[]> result = new HashMap<>();
        merged.forEach((term, others) -> result.put(term, others.toArray(String[]::new)));
        return Map.copyOf(result);
    }
}
