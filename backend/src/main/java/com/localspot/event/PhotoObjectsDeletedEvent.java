package com.localspot.event;

import java.util.List;

/** Bản ghi ảnh đã xóa trong transaction hiện tại; object trên kho chỉ xóa sau khi commit. */
public record PhotoObjectsDeletedEvent(List<String> keys) {

    public PhotoObjectsDeletedEvent {
        keys = List.copyOf(keys);
    }
}
