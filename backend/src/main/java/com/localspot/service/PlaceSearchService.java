package com.localspot.service;

import com.localspot.config.SearchProperties;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.dto.response.SearchPage;
import com.localspot.entity.Place;
import com.localspot.entity.PlaceStatus;
import com.localspot.listener.SearchIndexPublisher;
import com.localspot.repository.PlaceFilter;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.PlaceSort;
import com.localspot.search.PlaceSearchIndex;
import com.localspot.search.PlaceSearchQuery;
import com.localspot.search.SearchSort;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tìm kiếm địa điểm (UC08, FR-09): bỏ từ chung chung khỏi từ khóa ({@link SearchQueries}), Meilisearch trả id theo độ
 * liên quan, rồi nạp thẻ địa điểm từ MySQL theo đúng thứ tự (UC08 bước 3–4).
 *
 * <ul>
 *   <li><b>Nạp lại từ MySQL</b> thay vì trả thẳng dữ liệu trong index: ảnh bìa, điểm… luôn mới, và chỉ trả địa điểm
 *       <b>đang</b> APPROVED — index có lệch (message đồng bộ bị mất) cũng không lộ địa điểm đã ẩn. Id lệch như vậy
 *       được đẩy đi đồng bộ lại (tự sửa).
 *   <li><b>Fallback (U9)</b>: Meilisearch lỗi → MySQL {@code LIKE} trên tên, {@code degraded = true}, log WARN. Sau mỗi
 *       lần lỗi, ngừng gọi Meilisearch trong {@code retry-after-failure} (mặc định 30 s) — client không cho đặt timeout
 *       (mặc định 10 s), máy chủ treo sẽ làm mọi request chờ nếu cứ thử lại ngay.
 *   <li>Không mở transaction trong lúc gọi Meilisearch — chỉ mở khi nạp từ MySQL.
 * </ul>
 */
@Service
public class PlaceSearchService {

    private static final Logger log = LoggerFactory.getLogger(PlaceSearchService.class);

    private final PlaceSearchIndex index;
    private final PlaceRepository places;
    private final PlaceSummaries summaries;
    private final SearchIndexPublisher reindex;
    private final SearchProperties properties;
    private final TransactionTemplate readOnlyTx;
    private final Clock clock;
    private volatile Instant meilisearchRetryAt = Instant.MIN;

    public PlaceSearchService(
            PlaceSearchIndex index,
            PlaceRepository places,
            PlaceSummaries summaries,
            SearchIndexPublisher reindex,
            SearchProperties properties,
            TransactionTemplate tx,
            Clock clock) {
        this.index = index;
        this.places = places;
        this.summaries = summaries;
        this.reindex = reindex;
        this.properties = properties;
        this.readOnlyTx = new TransactionTemplate(tx.getTransactionManager());
        this.readOnlyTx.setReadOnly(true);
        this.clock = clock;
    }

    public SearchPage search(String q, PlaceFilter filter, SearchSort sort, String cursor, Integer limit) {
        int pageSize = KeysetCursor.limit(limit);
        int offset = SearchCursor.decode(cursor, sort);
        // Lấy dư một kết quả để biết còn trang sau, trong cửa sổ tối đa của Meilisearch
        int window = Math.min(pageSize + 1, PlaceSearchIndex.MAX_TOTAL_HITS - offset);
        PlaceSearchQuery query =
                new PlaceSearchQuery(SearchQueries.withoutGenericWords(q), filter, sort, offset, window);

        List<Long> ids = searchMeilisearch(query);
        boolean degraded = ids == null;
        if (degraded) {
            ids = places.findApprovedIdsByNameWords(query.q(), filter, fallbackSort(sort), offset, window);
        }
        boolean hasMore = ids.size() > pageSize;
        List<Long> pageIds = hasMore ? ids.subList(0, pageSize) : ids;
        String next = hasMore ? SearchCursor.encode(sort, offset + pageSize) : null;
        return new SearchPage(load(pageIds, !degraded), next, degraded);
    }

    /** {@code null} = không dùng được Meilisearch (lỗi lúc này, hoặc đang trong thời gian chờ sau lần lỗi trước). */
    private List<Long> searchMeilisearch(PlaceSearchQuery query) {
        if (clock.instant().isBefore(meilisearchRetryAt)) {
            return null;
        }
        try {
            return index.search(query);
        } catch (MeilisearchException e) {
            meilisearchRetryAt = clock.instant().plus(properties.retryAfterFailure());
            log.warn(
                    "Meilisearch lỗi, tìm kiếm dùng fallback MySQL LIKE trong {}: {}",
                    properties.retryAfterFailure(),
                    e.toString());
            return null;
        }
    }

    /** Thẻ địa điểm theo đúng thứ tự id; bỏ id không còn APPROVED (index lệch) và đưa chúng đi đồng bộ lại. */
    private List<PlaceSummaryResponse> load(List<Long> ids, boolean fromIndex) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return readOnlyTx.execute(status -> {
            Map<Long, Place> approved = places.findWithCategoryByIdIn(ids).stream()
                    .filter(place -> place.getStatus() == PlaceStatus.APPROVED)
                    .collect(Collectors.toMap(Place::getId, Function.identity()));
            List<Long> stale =
                    ids.stream().filter(id -> !approved.containsKey(id)).toList();
            if (fromIndex && !stale.isEmpty()) {
                reindex.enqueue(stale);
            }
            return summaries.of(ids.stream()
                    .map(approved::get)
                    .filter(place -> place != null)
                    .toList());
        });
    }

    /** {@code LIKE} không có độ liên quan → xếp theo điểm Bayesian như danh sách mặc định. */
    private static PlaceSort fallbackSort(SearchSort sort) {
        return switch (sort) {
            case RELEVANCE, SCORE -> PlaceSort.SCORE;
            case NEWEST -> PlaceSort.NEWEST;
            case MOST_REVIEWED -> PlaceSort.MOST_REVIEWED;
        };
    }
}
