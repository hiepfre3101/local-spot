package com.localspot.amqp;

import com.localspot.config.RabbitConfig;
import com.localspot.service.PlaceIndexingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Đồng bộ index tìm kiếm từ queue {@code search.reindex} (plan §7). Meilisearch lỗi → ném ra để listener retry, hết
 * lượt sang DLQ (NFR-13). Một consumer duy nhất (mặc định) nên các lần ghi cùng địa điểm không chen nhau; mỗi lần đều
 * đọc trạng thái hiện tại trong CSDL nên xử lý lặp hay sai thứ tự vẫn ra kết quả đúng.
 */
@Component
public class SearchReindexConsumer {

    private final PlaceIndexingService indexing;

    public SearchReindexConsumer(PlaceIndexingService indexing) {
        this.indexing = indexing;
    }

    @RabbitListener(queues = RabbitConfig.SEARCH_QUEUE)
    public void reindex(SearchReindexMessage message) {
        indexing.sync(message.placeIds());
    }
}
