package com.localspot.listener;

import com.localspot.service.RatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job đêm (D4): tính lại điểm mọi địa điểm với C mới — C đổi sau mỗi review làm điểm của mọi địa điểm khác lỗi thời,
 * nhưng đổi rất chậm khi đã nhiều review nên cập nhật một lần mỗi đêm là đủ. Đồng thời đối soát sự kiện tính lại bị mất.
 *
 * <p>Giả định một instance backend (triển khai đồ án); nhiều instance thì cần khóa phân tán để job không chạy trùng —
 * chạy trùng vẫn ra đúng kết quả (tính lại từ đầu), chỉ tốn công.
 */
@Component
public class RatingRecalculationJob {

    private static final Logger log = LoggerFactory.getLogger(RatingRecalculationJob.class);

    private final RatingService ratingService;

    public RatingRecalculationJob(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @Scheduled(cron = "${localspot.rating.recalculation-cron}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        long started = System.nanoTime();
        int changed = ratingService.recalculateAll();
        log.info(
                "Tính lại rating toàn bộ: {} địa điểm đổi điểm trong {} ms",
                changed,
                (System.nanoTime() - started) / 1_000_000);
    }
}
