package com.localspot.service;

import com.localspot.config.ReviewProperties;
import com.localspot.repository.ReviewRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Cảnh báo IP (FR-23, U3): địa điểm đã nhận ≥ 3 review từ cùng một dải IP ({@link IpPrefixes}) trong 24 giờ → review kế
 * tiếp từ dải đó vào hàng chờ bất kể trust và được gắn cờ "nghi ngờ IP" cho kiểm duyệt viên. Đếm cả review đã xóa.
 */
@Component
public class IpAnomalyDetector {

    private final ReviewRepository reviews;
    private final ReviewProperties properties;

    public IpAnomalyDetector(ReviewRepository reviews, ReviewProperties properties) {
        this.reviews = reviews;
        this.properties = properties;
    }

    public boolean isSuspicious(Long placeId, String ipPrefix, Instant now) {
        return reviews.countFromIpPrefixSince(placeId, ipPrefix, now.minus(properties.ipWindow()))
                >= properties.ipAlertCount();
    }
}
