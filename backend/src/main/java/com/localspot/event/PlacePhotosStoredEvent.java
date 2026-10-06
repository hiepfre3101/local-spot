package com.localspot.event;

import java.util.List;

/**
 * Ảnh gốc đã lên kho ({@code incoming/…}) và có bản ghi PROCESSING trong transaction hiện tại. Commit → đưa vào hàng
 * đợi xử lý; rollback → xóa ảnh gốc vừa upload (không để rác trong kho). Xem {@code PhotoQueuePublisher}.
 */
public record PlacePhotosStoredEvent(List<Long> photoIds, List<String> incomingKeys) {

    public PlacePhotosStoredEvent {
        photoIds = List.copyOf(photoIds);
        incomingKeys = List.copyOf(incomingKeys);
    }
}
