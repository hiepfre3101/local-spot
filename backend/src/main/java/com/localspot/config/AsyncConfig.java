package com.localspot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật {@code @Async} (tính lại rating sau commit) và {@code @Scheduled} (job đêm). Executor dùng
 * {@code applicationTaskExecutor} mặc định của Spring Boot (cấu hình qua {@code spring.task.execution.*}).
 *
 * <p>Lỗi trong phương thức {@code @Async void} không có ai nhận → ghi ERROR kèm tham số để lần theo; giá trị dẫn xuất
 * (điểm) được job đêm tự sửa.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (error, method, params) -> log.error(
                "Tác vụ nền {}.{} lỗi, tham số {}",
                method.getDeclaringClass().getSimpleName(),
                method.getName(),
                params,
                error);
    }
}
