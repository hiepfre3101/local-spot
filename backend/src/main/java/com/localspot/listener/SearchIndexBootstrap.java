package com.localspot.listener;

import com.localspot.config.SearchProperties;
import com.localspot.repository.PlaceRepository;
import com.localspot.search.PlaceSearchIndex;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Khi ứng dụng sẵn sàng: áp cấu hình index (synonym, thuộc tính tìm / lọc / xếp…) và đẩy toàn bộ địa điểm đã duyệt vào
 * queue đồng bộ. Đồng bộ đi qua cùng queue với các thay đổi thường ngày — một consumer ghi theo thứ tự, không có hai
 * luồng ghi chen nhau. Nhờ vậy index mới (dev mới cài, đổi phiên bản Meilisearch) tự đầy, và message đồng bộ bị mất
 * (không có outbox) được bù ở lần khởi động sau.
 *
 * <p>Meilisearch sập lúc khởi động không chặn ứng dụng: chỉ log, cấu hình được áp ở lần ghi đầu tiên của consumer.
 */
@Component
public class SearchIndexBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexBootstrap.class);

    private final PlaceSearchIndex index;
    private final PlaceRepository places;
    private final SearchIndexPublisher publisher;
    private final SearchProperties properties;

    public SearchIndexBootstrap(
            PlaceSearchIndex index,
            PlaceRepository places,
            SearchIndexPublisher publisher,
            SearchProperties properties) {
        this.index = index;
        this.places = places;
        this.publisher = publisher;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            index.applySettings();
        } catch (MeilisearchException e) {
            log.warn("Chưa áp được cấu hình index Meilisearch (sẽ thử lại khi đồng bộ): {}", e.toString());
        }
        if (properties.reindexOnStartup()) {
            List<Long> ids = places.findApprovedIds();
            publisher.enqueue(ids);
            log.info("Đã yêu cầu đồng bộ index tìm kiếm cho {} địa điểm đã duyệt", ids.size());
        }
    }
}
