package com.localspot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code localspot.mail.*} — địa chỉ người gửi. Kết nối SMTP dùng {@code spring.mail.*} chuẩn của Spring Boot (đặt tên
 * lớp khác {@code MailProperties} của Boot để không nhầm).
 */
@Validated
@ConfigurationProperties("localspot.mail")
public record MailSenderProperties(@NotBlank String from) {}
