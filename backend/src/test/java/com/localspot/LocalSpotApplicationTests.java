package com.localspot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Smoke test: context khởi động được trên MySQL/RabbitMQ/Redis thật (Testcontainers), Flyway chạy xong. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class LocalSpotApplicationTests {

    @Test
    void contextLoads() {}
}
