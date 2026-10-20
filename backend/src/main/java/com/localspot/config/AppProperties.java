package com.localspot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code localspot.app.*} — URL trang web công khai, dùng dựng link trong email. */
@Validated
@ConfigurationProperties("localspot.app")
public record AppProperties(@NotBlank String publicUrl) {

    /** Ghép đường dẫn frontend (sitemap) vào URL gốc, không nhân đôi dấu "/". */
    public String link(String path) {
        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        return base + path;
    }
}
