package com.localspot.amqp;

import java.util.List;

/** Message queue {@code search.reindex}: chỉ mang id (≤ 100), consumer đọc dữ liệu mới nhất từ CSDL. */
public record SearchReindexMessage(List<Long> placeIds) {}
