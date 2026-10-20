package com.localspot.service;

import com.localspot.config.RatingProperties;
import com.localspot.entity.PlaceStatus;
import com.localspot.event.PlaceIndexChangedEvent;
import com.localspot.repository.PlaceRatingStats;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.ReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.DoubleSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tính lại {@code places.review_count}, {@code avg_rating}, {@code bayesian_score} (FR-22, database.md §4) — nơi duy nhất
 * ghi ba cột này. Luôn đếm lại từ đầu từ bảng {@code reviews} (không cộng dồn ± 1): chạy lặp, chạy trễ hay chạy chen
 * nhau vẫn ra đúng một kết quả, và mất một sự kiện thì lần tính sau tự sửa.
 *
 * <ul>
 *   <li>{@link #recalculate(Long)} — sau mỗi thay đổi tập review PUBLISHED của địa điểm ({@code
 *       RecalculateRatingListener}): tính C mới rồi điểm của <b>địa điểm đó</b>.
 *   <li>{@link #recalculateAll()} — job đêm (D4): C đổi làm điểm của mọi địa điểm khác lỗi thời; đồng thời là job đối
 *       soát cho sự kiện bị mất (máy chủ tắt giữa chừng — sự kiện {@code @Async} chỉ nằm trong bộ nhớ).
 * </ul>
 *
 * Làm tròn theo kiểu cột ({@code DECIMAL(3,2)}, {@code DECIMAL(4,3)}), nửa lên; điểm Bayesian tính từ R <b>chưa làm
 * tròn</b>. Điểm của địa điểm đã duyệt đổi → phát {@link PlaceIndexChangedEvent} (điểm và số review nằm trong index
 * Meilisearch để lọc / xếp).
 */
@Service
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    private final PlaceRepository places;
    private final ReviewRepository reviews;
    private final RatingCalculator calculator;
    private final RatingProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    public RatingService(
            PlaceRepository places,
            ReviewRepository reviews,
            RatingCalculator calculator,
            RatingProperties properties,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.places = places;
        this.reviews = reviews;
        this.calculator = calculator;
        this.properties = properties;
        this.events = events;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Tính lại một địa điểm với C hiện tại. C là một {@code AVG} trên toàn bảng review — chấp nhận ở quy mô đồ án
     * (vài nghìn review); D4 đã chốt cập nhật điểm địa điểm liên quan mỗi sự kiện, còn lại để job đêm.
     *
     * @return {@code true} nếu điểm đổi
     */
    @Transactional
    public boolean recalculate(Long placeId) {
        return recalculate(placeId, this::systemAverage);
    }

    /**
     * Mọi địa điểm với cùng một C, mỗi địa điểm một transaction ngắn (khóa một dòng, không giữ khóa cả bảng trong lúc
     * job chạy — sự kiện tính lại xen giữa chỉ chờ đúng địa điểm đang xử lý).
     *
     * @return số địa điểm có điểm đổi
     */
    public int recalculateAll() {
        double systemAverage = tx.execute(status -> systemAverage());
        int changed = 0;
        for (Long placeId : places.findAllIdsForRating()) {
            if (Boolean.TRUE.equals(tx.execute(status -> recalculate(placeId, () -> systemAverage)))) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * Khóa dòng địa điểm là câu lệnh <b>đầu tiên</b> của transaction: ở mức REPEATABLE READ (mặc định MySQL), ảnh chụp dữ
     * liệu của transaction được chốt ở lần đọc thường đầu tiên — đọc C hay đếm review trước khi khóa thì phép đếm dùng
     * ảnh chụp cũ hơn lúc có khóa, và lần tính chạy sau có thể ghi đè điểm mới bằng điểm cũ.
     */
    private boolean recalculate(Long placeId, DoubleSupplier systemAverageSupplier) {
        List<Object[]> locked = places.lockRatingById(placeId);
        if (locked.isEmpty()) {
            log.warn("Bỏ qua tính lại rating: địa điểm {} không tồn tại / đã xóa", placeId);
            return false;
        }
        Object[] current = locked.getFirst();
        PlaceRatingStats stats = reviews.findRatingStats(placeId); // sau khi khóa: ảnh chụp mới nhất
        double systemAverage = systemAverageSupplier.getAsDouble();

        long reviewCount = stats.reviewCount();
        double average = reviewCount == 0 ? 0 : stats.average();
        BigDecimal avgRating = BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bayesianScore = BigDecimal.valueOf(
                        calculator.bayesian(reviewCount, average, properties.minReviews(), systemAverage))
                .setScale(3, RoundingMode.HALF_UP);

        boolean changed = ((Number) current[1]).longValue() != reviewCount
                || ((BigDecimal) current[2]).compareTo(avgRating) != 0
                || ((BigDecimal) current[3]).compareTo(bayesianScore) != 0;
        if (!changed) {
            return false;
        }
        places.updateRating(placeId, reviewCount, avgRating, bayesianScore);
        if (PlaceStatus.APPROVED.name().equals(current[0])) {
            events.publishEvent(PlaceIndexChangedEvent.of(placeId));
        }
        return true;
    }

    /** C; hệ thống chưa có review nào thì mọi địa điểm có v = 0 → C không được dùng. */
    private double systemAverage() {
        Double average = reviews.findSystemAverageRating();
        return average == null ? 0 : average;
    }
}
