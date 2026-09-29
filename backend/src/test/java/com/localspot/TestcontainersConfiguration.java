package com.localspot;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Hạ tầng thật cho test tích hợp. Phiên bản image khớp docker-compose.yml để test chạy trên đúng
 * MySQL/RabbitMQ/Redis của môi trường dev và deploy (spatial index, hành vi collation...).
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

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
}
