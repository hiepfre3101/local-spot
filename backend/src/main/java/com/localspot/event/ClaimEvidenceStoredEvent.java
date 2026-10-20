package com.localspot.event;

import java.util.List;

/**
 * File minh chứng của một yêu cầu sở hữu vừa lên kho trong transaction đang chạy (UC23). Transaction rollback → các key
 * này bị xóa ({@code ClaimEvidenceCleanupListener}), không để file riêng tư mồ côi trong kho.
 */
public record ClaimEvidenceStoredEvent(List<String> keys) {

    public ClaimEvidenceStoredEvent {
        keys = List.copyOf(keys);
    }
}
