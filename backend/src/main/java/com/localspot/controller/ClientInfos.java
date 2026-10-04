package com.localspot.controller;

import com.localspot.service.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/** Dựng {@link ClientInfo} từ request HTTP — giữ servlet API ở tầng controller, service không phụ thuộc web. */
final class ClientInfos {

    private ClientInfos() {}

    /** IP lấy từ {@code getRemoteAddr()}: sau nginx, {@code forward-headers-strategy} đã thay bằng IP thật. */
    static ClientInfo from(HttpServletRequest http) {
        return new ClientInfo(http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
    }
}
