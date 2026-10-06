package com.localspot.listener;

import com.localspot.event.CatalogChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Xóa cache danh mục / tiện ích <b>sau commit</b> và <b>chờ xóa xong</b>.
 *
 * <p>Không dùng {@code @CacheEvict(allEntries = true)}: annotation đó gọi {@code Cache.clear()}, mà ở Spring Data Redis 4
 * {@code clear()} chạy bất đồng bộ (không chờ Redis) — request đọc ngay sau có thể vẫn lấy cây cũ (lỗi lộ ra khi chạy
 * cả bộ test). {@code invalidate()} thì đồng bộ, nhưng {@code @CacheEvict(beforeInvocation = true)} lại xóa trước khi
 * commit → một request đọc chen giữa sẽ nạp lại dữ liệu cũ. Sự kiện AFTER_COMMIT + {@code invalidate()} tránh cả hai.
 *
 * <p>Redis lỗi chỉ ghi log (cache có thể cũ tới hết TTL) — thao tác quản trị đã commit vẫn thành công.
 */
@Component
public class CatalogCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(CatalogCacheInvalidator.class);

    private final CacheManager cacheManager;

    public CatalogCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidate(CatalogChangedEvent event) {
        Cache cache = cacheManager.getCache(event.cacheName());
        if (cache == null) {
            return;
        }
        try {
            cache.invalidate();
        } catch (RuntimeException e) {
            log.warn("Không xóa được cache '{}' (có thể cũ tới hết TTL): {}", event.cacheName(), e.getMessage());
        }
    }
}
