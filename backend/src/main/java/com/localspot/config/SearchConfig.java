package com.localspot.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Client Meilisearch (Java client chính thức — plan §2). Tạo client không gọi mạng, nên Meilisearch sập lúc khởi động
 * không làm ứng dụng lỗi: tìm kiếm tự chuyển sang fallback MySQL (U9).
 *
 * <p>Client không cho đặt timeout HTTP (dùng mặc định 10 s của OkHttp) — {@code PlaceSearchService} bù bằng cách ngừng
 * gọi Meilisearch một lúc sau mỗi lần lỗi ({@code localspot.search.retry-after-failure}).
 */
@Configuration(proxyBeanMethods = false)
public class SearchConfig {

    @Bean
    Client meilisearchClient(SearchProperties properties) {
        return new Client(new Config(properties.host(), properties.apiKey()));
    }
}
