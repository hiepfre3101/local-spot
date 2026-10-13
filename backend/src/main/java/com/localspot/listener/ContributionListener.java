package com.localspot.listener;

import com.localspot.event.ContributionChangedEvent;
import com.localspot.event.ReviewChangedEvent;
import com.localspot.repository.ReviewRepository;
import com.localspot.service.ContributionService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tính lại điểm đóng góp + trao huy hiệu sau commit, chạy nền (như {@link RecalculateRatingListener}): rollback thì không
 * tính, người dùng không chờ, lỗi không làm hỏng request đã thành công. Mất sự kiện → {@link ContributionRecalculationJob}.
 */
@Component
public class ContributionListener {

    private final ContributionService contributions;
    private final ReviewRepository reviews;

    public ContributionListener(ContributionService contributions, ReviewRepository reviews) {
        this.contributions = contributions;
        this.reviews = reviews;
    }

    /** {@code fallbackExecution}: consumer xử lý ảnh phát sự kiện ngoài transaction. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ContributionChangedEvent event) {
        contributions.refresh(event.userId());
    }

    /** Dùng lại sự kiện của luồng rating (không sửa luồng lõi) — tập review đang hiện của tác giả vừa đổi. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReviewChangedEvent event) {
        reviews.findAuthorIdIncludingDeleted(event.reviewId()).ifPresent(contributions::refresh);
    }
}
