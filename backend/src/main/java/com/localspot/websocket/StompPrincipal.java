package com.localspot.websocket;

import java.security.Principal;

/**
 * Người dùng của một phiên STOMP. {@link #getName()} = id người dùng — khóa để
 * {@code convertAndSendToUser(id, "/queue/notifications", …)} tìm đúng các phiên của người đó.
 */
public record StompPrincipal(long userId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
