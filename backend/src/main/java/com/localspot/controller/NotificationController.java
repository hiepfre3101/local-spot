package com.localspot.controller;

import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.NotificationResponse;
import com.localspot.security.AuthenticatedUser;
import com.localspot.service.NotificationService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/me/notifications} — thông báo của chính người dùng (UC19, FR-27). Realtime: {@code /ws}. */
@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public CursorPage<NotificationResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return notifications.list(user.id(), unreadOnly, cursor, limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("count", notifications.unreadCount(user.id()));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long notificationId) {
        notifications.markRead(user.id(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        notifications.markAllRead(user.id());
        return ResponseEntity.noContent().build();
    }
}
