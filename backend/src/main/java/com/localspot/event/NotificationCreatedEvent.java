package com.localspot.event;

import com.localspot.dto.response.NotificationResponse;

/**
 * Một thông báo vừa được ghi trong transaction đang chạy — đẩy realtime sau khi commit ({@code NotificationPushListener}).
 * Mang sẵn nội dung đã dựng: listener chạy ngoài transaction, không phải đọc lại CSDL.
 */
public record NotificationCreatedEvent(long recipientId, NotificationResponse notification) {}
