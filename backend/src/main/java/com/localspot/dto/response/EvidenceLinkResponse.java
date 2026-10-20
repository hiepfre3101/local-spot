package com.localspot.dto.response;

import java.net.URI;
import java.time.Instant;

/**
 * openapi {@code EvidenceLink} — link tạm đọc một file minh chứng trong kho riêng tư (UC30). Trả JSON thay vì 302: access
 * token chỉ nằm trong bộ nhớ và gửi bằng header (S1), nên {@code <img src>} trỏ thẳng vào API không xác thực được; client
 * gọi API bằng axios rồi gán {@code url} vào ảnh, quá {@code expiresAt} thì gọi lại.
 */
public record EvidenceLinkResponse(URI url, Instant expiresAt) {}
