package com.localspot.listener;

import com.localspot.event.ReviewChangedEvent;
import com.localspot.service.RatingService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tính lại điểm địa điểm sau mỗi thay đổi tập review PUBLISHED (FR-22).
 *
 * <ul>
 *   <li><b>AFTER_COMMIT</b>: chỉ tính khi review thật sự đã lưu (rollback → không tính), và lúc tính đọc thấy dữ liệu
 *       mới.
 *   <li><b>{@code @Async}</b>: người viết review không phải chờ tính điểm; lỗi ở đây (CSDL chập chờn) không làm hỏng
 *       request đã thành công. Lỗi được {@code AsyncConfig} ghi log.
 * </ul>
 *
 * Đánh đổi: sự kiện chỉ nằm trong bộ nhớ — máy chủ tắt đúng lúc thì điểm lệch tới job đêm ({@code
 * RatingRecalculationJob}), vốn tính lại toàn bộ từ đầu. Chấp nhận thay vì thêm outbox / queue cho một giá trị tự sửa
 * được.
 */
@Component
public class RecalculateRatingListener {

    private final RatingService ratingService;

    public RecalculateRatingListener(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReviewChangedEvent event) {
        ratingService.recalculate(event.placeId());
    }
}
