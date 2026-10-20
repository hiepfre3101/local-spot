package com.localspot.service;

/** Thông tin thiết bị gắn với refresh token (giải trình phiên đăng nhập). Cắt theo độ dài cột. */
public record ClientInfo(String userAgent, String ipAddress) {

    private static final int USER_AGENT_MAX = 255;
    private static final int IP_MAX = 45;

    public ClientInfo {
        userAgent = truncate(userAgent, USER_AGENT_MAX);
        ipAddress = truncate(ipAddress, IP_MAX);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
