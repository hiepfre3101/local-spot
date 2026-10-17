package com.localspot.event;

import java.util.List;

/** Các bản ảnh trang chủ vừa lên kho trong transaction hiện tại — rollback thì xóa (không để rác trong kho). */
public record BannerImageStoredEvent(List<String> keys) {

    public BannerImageStoredEvent {
        keys = List.copyOf(keys);
    }
}
