package com.localspot;

import com.localspot.storage.InMemoryObjectStorage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Hạ tầng thật cho test tích hợp. Phiên bản image khớp docker-compose.yml để test chạy trên đúng
 * MySQL/RabbitMQ/Redis/Meilisearch của môi trường dev và deploy (spatial index, hành vi collation...).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(
                DockerImageName.parse("rabbitmq:3.13-management-alpine").asCompatibleSubstituteFor("rabbitmq"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    }

    static final String MEILI_KEY = "test-master-key";

    /** Meilisearch thật (không có {@code @ServiceConnection} cho Meilisearch → nối bằng {@link DynamicPropertyRegistrar}). */
    @Bean
    GenericContainer<?> meilisearchContainer() {
        return new GenericContainer<>(DockerImageName.parse("getmeili/meilisearch:v1.54"))
                .withEnv("MEILI_MASTER_KEY", MEILI_KEY)
                .withEnv("MEILI_NO_ANALYTICS", "true")
                .withExposedPorts(7700)
                .waitingFor(Wait.forHttp("/health").forStatusCode(200));
    }

    @Bean
    DynamicPropertyRegistrar meilisearchProperties(@Qualifier("meilisearchContainer") GenericContainer<?> meili) {
        return registry -> {
            registry.add("localspot.search.host", () -> "http://" + meili.getHost() + ":" + meili.getMappedPort(7700));
            registry.add("localspot.search.api-key", () -> MEILI_KEY);
        };
    }

    /**
     * Kho ảnh trong bộ nhớ thay MinIO: image MinIO chính thức không còn tải được (2026-10-06), và test chỉ cần hành vi
     * của {@code ObjectStorage}, không cần S3 thật.
     */
    @Bean
    @Primary
    InMemoryObjectStorage inMemoryObjectStorage() {
        return new InMemoryObjectStorage();
    }
}
