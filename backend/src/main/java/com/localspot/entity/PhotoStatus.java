package com.localspot.entity;

/** Trạng thái xử lý nền của ảnh (resize, nén qua RabbitMQ). */
public enum PhotoStatus {
    PROCESSING,
    READY,
    FAILED
}
