package com.localspot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Thay cấu hình transaction mặc định của Spring Boot chỉ để <b>đặt thứ tự advice</b>: mặc định advice transaction có độ
 * ưu tiên thấp nhất, trùng với {@code ActivityLogAspect} → thứ tự bọc giữa hai advice không xác định. Ưu tiên cao hơn
 * một bậc = transaction luôn bọc ngoài, aspect ghi nhật ký chạy bên trong cùng transaction với thao tác (FR-42).
 * {@code proxyTargetClass = true} giữ nguyên mặc định proxy CGLIB của Spring Boot.
 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(proxyTargetClass = true, order = TransactionConfig.TRANSACTION_ADVICE_ORDER)
public class TransactionConfig {

    public static final int TRANSACTION_ADVICE_ORDER = Ordered.LOWEST_PRECEDENCE - 1;
}
