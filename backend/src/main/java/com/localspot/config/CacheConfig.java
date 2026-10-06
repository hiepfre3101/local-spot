package com.localspot.config;

import com.localspot.dto.response.AmenityResponse;
import com.localspot.dto.response.CategoryNode;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Cache trên Redis (plan §2): cây danh mục và tiện ích — đọc ở mọi form / bộ lọc, gần như không đổi. Ghi qua API
 * quản trị thì xóa cache sau commit; TTL chỉ là lưới an toàn (vd. sửa tay trong CSDL).
 *
 * <p><b>Redis sập không làm hỏng request</b> (như rate limit — D8): lỗi cache chỉ ghi log, request đọc thẳng CSDL.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String CATEGORIES = "categories";
    public static final String AMENITIES = "amenities";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);
    private static final Duration TTL = Duration.ofHours(1);

    /**
     * Mỗi cache một kiểu cụ thể: JSON đọc được trong Redis, không phụ thuộc Java serialization hay type info. Xóa cache
     * khi ghi: {@code CatalogCacheInvalidator} (sau commit, đồng bộ).
     */
    @Bean
    RedisCacheManagerBuilderCustomizer catalogCaches(JsonMapper jsonMapper) {
        JavaType categories = jsonMapper.getTypeFactory().constructCollectionType(List.class, CategoryNode.class);
        JavaType amenities = jsonMapper.getTypeFactory().constructCollectionType(List.class, AmenityResponse.class);
        return builder -> builder.withCacheConfiguration(CATEGORIES, typed(jsonMapper, categories))
                .withCacheConfiguration(AMENITIES, typed(jsonMapper, amenities));
    }

    private static RedisCacheConfiguration typed(JsonMapper jsonMapper, JavaType type) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(TTL)
                .prefixCacheNameWith("localspot:")
                .disableCachingNullValues()
                .serializeValuesWith(
                        SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(jsonMapper, type)));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Đọc cache '{}' lỗi, dùng CSDL: {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Ghi cache '{}' lỗi: {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Xóa cache '{}' lỗi (có thể cũ tới hết TTL): {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Xóa cache '{}' lỗi (có thể cũ tới hết TTL): {}", cache.getName(), e.getMessage());
            }
        };
    }
}
