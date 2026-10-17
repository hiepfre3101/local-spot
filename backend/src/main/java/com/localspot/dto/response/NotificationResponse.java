package com.localspot.dto.response;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** openapi {@code Notification} — cũng là nội dung đẩy realtime qua {@code /user/queue/notifications}. */
public record NotificationResponse(Long id, String type, Map<String, Object> data, Instant readAt, Instant createdAt) {

    public NotificationResponse {
        data = Collections.unmodifiableMap(new LinkedHashMap<>(data)); // giá trị null hợp lệ (vd. không có lý do)
    }
}
