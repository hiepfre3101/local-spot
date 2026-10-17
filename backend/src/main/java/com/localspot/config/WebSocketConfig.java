package com.localspot.config;

import com.localspot.websocket.StompAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP qua WebSocket thuần ở {@code /ws} (FR-27; chốt 2026-10-16: không SockJS — mọi trình duyệt mục tiêu hỗ trợ
 * WebSocket, khỏi thêm thư viện client và nhánh HTTP polling). Broker đơn giản trong bộ nhớ: đủ cho một tiến trình trên
 * VPS (plan §9); chạy nhiều instance thì đổi sang relay STOMP của RabbitMQ (đã có trong stack) mà không đổi client.
 *
 * <p>Chỉ cho origin của frontend ({@code localspot.app.public-url}) mở kết nối — chặn trang lạ dùng trình duyệt của
 * người dùng để nghe trộm (token vẫn bắt buộc ở CONNECT, đây là lớp phòng thủ thêm).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties app;
    private final StompAuthInterceptor auth;

    public WebSocketConfig(AppProperties app, StompAuthInterceptor auth) {
        this.app = app;
        this.auth = auth;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(app.publicUrl());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(auth);
    }
}
