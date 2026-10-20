package com.localspot.listener;

import com.localspot.repository.ReviewRepository;
import com.localspot.service.ContributionService;
import com.localspot.service.ImageInspector.Format;
import com.localspot.service.ImageInspector.Inspected;
import com.localspot.service.RatingService;
import com.localspot.service.ReviewPhotoService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phần dữ liệu demo không làm được bằng SQL ({@code db/seed/dev/R__demo_03_reviews.sql}) — chỉ bật ở profile dev
 * ({@code localspot.demo.bootstrap = true}), không bao giờ ở prod:
 *
 * <ol>
 *   <li><b>Điểm địa điểm</b>: seed không chép công thức Bayesian ra SQL → tính lại toàn bộ bằng
 *       {@link RatingService#recalculateAll()} (chính job đêm D4). Idempotent, ~300 địa điểm mất vài giây.
 *   <li><b>Điểm đóng góp + huy hiệu</b>: {@link ContributionService#refreshAll()} (chính job đêm).
 *   <li><b>Ảnh review</b>: cần object trong kho → tạo ảnh minh họa (hình khối màu phẳng, không chữ — không phụ thuộc font
 *       của máy) rồi đi qua đúng đường của người dùng: {@link ReviewPhotoService#store} → ảnh gốc {@code incoming/} →
 *       queue {@code photo.process} → 3 bản JPEG. Chỉ thêm cho review demo chưa có ảnh nào → khởi động lại không nhân đôi.
 * </ol>
 *
 * <p>Giới hạn đã biết (chỉ dev): xóa volume MinIO nhưng giữ CSDL → bản ghi READY trỏ tới object không còn; xóa hết ảnh
 * của một review demo qua UI → lần khởi động sau ảnh minh họa được thêm lại.
 */
@Component
@ConditionalOnProperty(name = "localspot.demo.bootstrap", havingValue = "true")
public class DemoDataBootstrap {

    private static final Logger log = LoggerFactory.getLogger(DemoDataBootstrap.class);

    /** ~1/8 review demo 4–5★ đang hiện, 1–3 ảnh mỗi review; tất định theo (địa điểm, người viết). */
    private static final String PHOTO_CANDIDATES = """
            SELECT r.id, r.user_id, MOD(r.place_id + r.user_id, 3) + 1 AS photo_count
            FROM reviews r
            JOIN users u ON u.id = r.user_id
            WHERE u.email LIKE '%@localspot.test'
              AND r.status = 'PUBLISHED' AND r.deleted_at IS NULL AND r.rating >= 4
              AND MOD(r.place_id * 7 + r.user_id, 8) = 0
              AND NOT EXISTS (SELECT 1 FROM review_photos p WHERE p.review_id = r.id)
            ORDER BY r.id
            """;

    /** Màu phẳng, trầm — ảnh minh họa không được trông như ảnh thật của địa điểm. */
    private static final Color[] PALETTE = {
        new Color(0x8C6A4F),
        new Color(0x5E7D63),
        new Color(0x4F6D8C),
        new Color(0xA0704A),
        new Color(0x7A5C7E),
        new Color(0x6B7F3F),
        new Color(0x9B5D4F),
        new Color(0x4F8584)
    };

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 900;

    private final RatingService ratingService;
    private final ContributionService contributions;
    private final ReviewPhotoService reviewPhotos;
    private final ReviewRepository reviews;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public DemoDataBootstrap(
            RatingService ratingService,
            ContributionService contributions,
            ReviewPhotoService reviewPhotos,
            ReviewRepository reviews,
            JdbcTemplate jdbc,
            TransactionTemplate tx) {
        this.ratingService = ratingService;
        this.contributions = contributions;
        this.reviewPhotos = reviewPhotos;
        this.reviews = reviews;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        recalculateRatings();
        refreshContributions();
        attachReviewPhotos();
    }

    /** Điểm đóng góp + huy hiệu cho dữ liệu seed — công thức chỉ ở Java như rating. Ảnh minh họa xử lý xong tự cộng thêm. */
    public void refreshContributions() {
        long started = System.nanoTime();
        int awarded = contributions.refreshAll();
        log.info(
                "Demo: tính điểm đóng góp — {} huy hiệu mới trong {} ms",
                awarded,
                (System.nanoTime() - started) / 1_000_000);
    }

    public void recalculateRatings() {
        long started = System.nanoTime();
        int changed = ratingService.recalculateAll();
        log.info(
                "Demo: tính lại rating — {} địa điểm đổi điểm trong {} ms",
                changed,
                (System.nanoTime() - started) / 1_000_000);
    }

    public void attachReviewPhotos() {
        List<Candidate> candidates = jdbc.query(
                PHOTO_CANDIDATES,
                (rs, row) -> new Candidate(rs.getLong("id"), rs.getLong("user_id"), rs.getInt("photo_count")));
        int stored = 0;
        for (Candidate candidate : candidates) {
            List<Inspected> images = new ArrayList<>(candidate.photoCount());
            for (int i = 0; i < candidate.photoCount(); i++) {
                images.add(new Inspected(placeholderJpeg(candidate.reviewId() * 31 + i), Format.JPEG));
            }
            try {
                Integer added = tx.execute(status -> reviews.findByIdForUpdate(candidate.reviewId())
                        .map(review -> reviewPhotos
                                .store(review, candidate.authorId(), images)
                                .size())
                        .orElse(0));
                stored += added == null ? 0 : added;
            } catch (RuntimeException e) {
                // Kho ảnh chưa chạy (docker compose thiếu minio…) — không chặn khởi động, lần sau thử lại
                log.warn("Demo: dừng thêm ảnh review sau {} ảnh: {}", stored, e.getMessage());
                return;
            }
        }
        if (stored > 0) {
            log.info("Demo: đã thêm {} ảnh minh họa cho {} review (xử lý tiếp qua queue)", stored, candidates.size());
        }
    }

    /** Ảnh minh họa tất định theo {@code seed}: nền một màu + vài hình khối màu khác (không gradient, không chữ). */
    static byte[] placeholderJpeg(long seed) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int base = (int) Math.floorMod(seed, PALETTE.length);
            g.setColor(PALETTE[base]);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(PALETTE[(base + 3) % PALETTE.length].brighter());
            int d = 360 + (int) Math.floorMod(seed * 7, 240);
            g.fillOval((int) Math.floorMod(seed * 13, WIDTH - d), (int) Math.floorMod(seed * 17, HEIGHT - d), d, d);
            g.setColor(PALETTE[(base + 5) % PALETTE.length]);
            g.fillRect(0, HEIGHT - 180 - (int) Math.floorMod(seed * 5, 120), WIDTH, 300);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpg", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private record Candidate(long reviewId, long authorId, int photoCount) {}
}
