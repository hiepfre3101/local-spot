package com.localspot.listener;

import com.localspot.service.ContributionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job đêm: tính lại điểm đóng góp + trao huy hiệu cho mọi người — đối soát sự kiện bị mất (sự kiện {@code @Async} chỉ
 * nằm trong bộ nhớ) và nội dung bị ẩn / hiện lại qua đường không phát sự kiện. Giả định một instance như
 * {@link RatingRecalculationJob}; chạy trùng vẫn đúng.
 */
@Component
public class ContributionRecalculationJob {

    private static final Logger log = LoggerFactory.getLogger(ContributionRecalculationJob.class);

    private final ContributionService contributions;

    public ContributionRecalculationJob(ContributionService contributions) {
        this.contributions = contributions;
    }

    @Scheduled(cron = "${localspot.contribution.recalculation-cron}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        long started = System.nanoTime();
        int awarded = contributions.refreshAll();
        log.info(
                "Tính lại điểm đóng góp toàn bộ: {} huy hiệu mới trong {} ms",
                awarded,
                (System.nanoTime() - started) / 1_000_000);
    }
}
