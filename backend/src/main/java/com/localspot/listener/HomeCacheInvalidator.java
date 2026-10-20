package com.localspot.listener;

import com.localspot.config.CacheConfig;
import com.localspot.event.CatalogChangedEvent;
import com.localspot.event.HomeBannersChangedEvent;
import com.localspot.event.PlaceIndexChangedEvent;
import com.localspot.event.ReviewChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Xóa cache trang chủ ({@code HomeService}) <b>sau commit</b>, đồng bộ — cùng lý do với {@link CatalogCacheInvalidator}
 * ({@code invalidate()} chờ Redis xóa xong; xóa trước commit thì request chen giữa nạp lại dữ liệu cũ).
 *
 * <p>Nghe lại các sự kiện đã có thay vì phát sự kiện riêng: {@link PlaceIndexChangedEvent} phủ duyệt địa điểm, chủ sửa
 * thông tin, đổi tên danh mục và điểm Bayesian đổi; {@link ReviewChangedEvent} phủ tập review đang hiện đổi (đánh giá nổi
 * bật có thể vừa bị xóa / ẩn); {@link CatalogChangedEvent} của danh mục phủ cây danh mục trong response.
 */
@Component
public class HomeCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(HomeCacheInvalidator.class);

    private final CacheManager cacheManager;

    public HomeCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlacesChanged(PlaceIndexChangedEvent event) {
        invalidate();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewChanged(ReviewChangedEvent event) {
        invalidate();
    }

    /** Ảnh trang chủ đổi (UC34). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBannersChanged(HomeBannersChangedEvent event) {
        invalidate();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCatalogChanged(CatalogChangedEvent event) {
        if (CacheConfig.CATEGORIES.equals(event.cacheName())) {
            invalidate();
        }
    }

    private void invalidate() {
        Cache cache = cacheManager.getCache(CacheConfig.HOME);
        if (cache == null) {
            return;
        }
        try {
            cache.invalidate();
        } catch (RuntimeException e) {
            log.warn("Không xóa được cache trang chủ (có thể cũ tới hết TTL): {}", e.getMessage());
        }
    }
}
