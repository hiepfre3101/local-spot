package com.localspot.amqp;

/**
 * Message trên queue {@code photo.process}: chỉ mang id — ảnh gốc nằm ở kho object ({@code incoming/…}), không đưa vài
 * MB nhị phân qua RabbitMQ.
 */
public record PhotoProcessMessage(long placePhotoId) {}
