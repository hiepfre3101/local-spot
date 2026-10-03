package com.localspot.security;

import java.io.Serializable;

/**
 * Principal trong SecurityContext — chỉ giữ giá trị bất biến, không giữ JPA entity (tránh lazy loading ngoài
 * transaction và rò rỉ entity sang controller). Lấy trong controller bằng {@code @AuthenticationPrincipal}.
 */
public record AuthenticatedUser(Long id, String email) implements Serializable {}
