package com.localspot.event;

import com.localspot.amqp.PhotoProcessMessage.Target;
import java.util.List;

/**
 * Ảnh gốc đã lên kho ({@code incoming/…}) và có bản ghi PROCESSING trong transaction hiện tại — ảnh địa điểm hoặc ảnh
 * review ({@code target}). Commit → đưa vào hàng đợi xử lý; rollback → xóa ảnh gốc vừa upload (không để rác trong kho).
 * Xem {@code PhotoQueuePublisher}.
 */
public record PhotosStoredEvent(Target target, List<Long> photoIds, List<String> incomingKeys) {

    public PhotosStoredEvent {
        photoIds = List.copyOf(photoIds);
        incomingKeys = List.copyOf(incomingKeys);
    }
}
