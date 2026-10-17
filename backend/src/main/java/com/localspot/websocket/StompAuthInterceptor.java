package com.localspot.websocket;

import com.localspot.security.AuthenticatedUser;
import com.localspot.security.UserJwtAuthenticationConverter;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Xác thực và giới hạn khung STOMP vào (FR-27, chốt 2026-10-16). Access token chỉ nằm trong bộ nhớ (S1), trình duyệt
 * không gắn được header vào handshake WebSocket → client gửi {@code Authorization: Bearer …} trong khung
 * {@code CONNECT}; kiểm bằng đúng {@link JwtDecoder} + {@link UserJwtAuthenticationConverter} của REST (chữ ký, hạn,
 * tài khoản bị khóa / đã xóa).
 *
 * <ul>
 *   <li>{@code CONNECT} thiếu / sai token → lỗi, phiên bị đóng.
 *   <li>{@code SUBSCRIBE} chỉ được vào kênh cá nhân {@link #NOTIFICATION_DESTINATION} — broker tự gắn phiên của chính
 *       người đó, không nghe được kênh người khác.
 *   <li>{@code SEND} bị chặn: kênh chỉ đẩy một chiều từ server.
 * </ul>
 *
 * Giới hạn đã biết: token chỉ kiểm lúc kết nối — phiên mở tiếp sau khi token hết hạn 15 phút cho tới khi ngắt (chỉ nhận
 * thông báo của chính mình, không gọi được API nào).
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    public static final String NOTIFICATION_DESTINATION = "/user/queue/notifications";
    private static final String BEARER = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final UserJwtAuthenticationConverter converter;

    public StompAuthInterceptor(JwtDecoder jwtDecoder, UserJwtAuthenticationConverter converter) {
        this.jwtDecoder = jwtDecoder;
        this.converter = converter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)));
        } else if (command == StompCommand.SUBSCRIBE) {
            if (accessor.getUser() == null || !NOTIFICATION_DESTINATION.equals(accessor.getDestination())) {
                throw new MessageDeliveryException("Không được đăng ký kênh " + accessor.getDestination());
            }
        } else if (command == StompCommand.SEND) {
            throw new MessageDeliveryException("Kênh thông báo chỉ nhận, không gửi");
        }
        return message;
    }

    private StompPrincipal authenticate(String header) {
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessageDeliveryException("Thiếu access token");
        }
        try {
            var authentication = converter.convert(jwtDecoder.decode(header.substring(BEARER.length())));
            if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
                throw new MessageDeliveryException("Access token không hợp lệ");
            }
            return new StompPrincipal(user.id());
        } catch (JwtException | AuthenticationException e) {
            throw new MessageDeliveryException("Access token không hợp lệ: " + e.getMessage());
        }
    }
}
