package com.localspot.listener;

import com.localspot.event.BannerImageStoredEvent;
import com.localspot.storage.ObjectStorage;
import com.localspot.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Dọn ảnh trang chủ vừa tải khi thao tác không được lưu (rollback) — cùng cách với {@link ClaimEvidenceCleanupListener}. */
@Component
public class BannerImageCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(BannerImageCleanupListener.class);

    private final ObjectStorage storage;

    public BannerImageCleanupListener(ObjectStorage storage) {
        this.storage = storage;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void discard(BannerImageStoredEvent event) {
        if (event.keys().isEmpty()) {
            return;
        }
        try {
            storage.delete(event.keys());
        } catch (StorageException e) {
            log.warn(
                    "Không xóa được {} object ảnh trang chủ sau rollback: {}",
                    event.keys().size(),
                    e.getMessage());
        }
    }
}
