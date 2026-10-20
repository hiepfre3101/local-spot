package com.localspot.listener;

import com.localspot.event.ClaimEvidenceStoredEvent;
import com.localspot.storage.ObjectStorage;
import com.localspot.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Dọn file minh chứng khi yêu cầu sở hữu không được lưu (rollback) — cùng cách với ảnh gốc ở {@code PhotoQueuePublisher}. */
@Component
public class ClaimEvidenceCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(ClaimEvidenceCleanupListener.class);

    private final ObjectStorage storage;

    public ClaimEvidenceCleanupListener(ObjectStorage storage) {
        this.storage = storage;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void discard(ClaimEvidenceStoredEvent event) {
        if (event.keys().isEmpty()) {
            return;
        }
        try {
            storage.delete(event.keys());
        } catch (StorageException e) {
            log.warn(
                    "Không xóa được {} file minh chứng sau rollback: {}",
                    event.keys().size(),
                    e.getMessage());
        }
    }
}
