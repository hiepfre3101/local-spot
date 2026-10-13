package com.localspot.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Điểm đóng góp (FR-29, O3 — chốt 2026-10-12) tính <b>từ dữ liệu gốc</b>, không lưu sổ điểm: chỉ nội dung đang hiển thị
 * mới có điểm, xóa / ẩn nội dung thì điểm tự mất, và cửa sổ thời gian (tuần / tháng) đúng theo ngày phát sinh.
 *
 * <pre>
 *   review đang hiện            +10, ngày = ngày viết review
 *   ảnh READY trong review đó   +2 / ảnh, tối đa 5 ảnh / review — tính theo ngày của review (trần đơn giản, chống
 *                               chất ảnh)
 *   vote hữu ích hợp lệ nhận    +1, ngày = ngày vote ({@code counted} — requirements §5.1)
 *   check-in                    +1, ngày check-in
 *   đề xuất địa điểm được duyệt +15, ngày duyệt
 * </pre>
 *
 * "Đang hiện" = review {@code PUBLISHED} chưa xóa trên địa điểm {@code APPROVED} chưa xóa; check-in / đề xuất cũng chỉ
 * tính khi địa điểm đang hiện. {@code city} (tùy chọn) lọc theo thành phố của địa điểm được đóng góp.
 */
@Repository
public class ContributionQueries {

    /** Mốc "toàn thời gian". */
    public static final Instant BEGINNING = Instant.EPOCH;

    private static final String VISIBLE_PLACE =
            "p.status = 'APPROVED' AND p.deleted_at IS NULL AND (:city IS NULL OR p.city = :city)";

    /** Điểm theo người trong cửa sổ {@code [:from, now)}; {@code :userId} NULL = mọi người. */
    private static final String POINTS = """
            SELECT x.user_id, SUM(x.points) AS points FROM (
              SELECT r.user_id, 10 + 2 * LEAST(5, (SELECT COUNT(*) FROM review_photos rp
                                                   WHERE rp.review_id = r.id AND rp.status = 'READY')) AS points
              FROM reviews r JOIN places p ON p.id = r.place_id
              WHERE r.status = 'PUBLISHED' AND r.deleted_at IS NULL AND {VISIBLE_PLACE} AND r.created_at >= :from
              UNION ALL
              SELECT r.user_id, 1
              FROM review_votes v JOIN reviews r ON r.id = v.review_id JOIN places p ON p.id = r.place_id
              WHERE v.counted AND r.status = 'PUBLISHED' AND r.deleted_at IS NULL AND {VISIBLE_PLACE} AND v.created_at >= :from
              UNION ALL
              SELECT c.user_id, 1
              FROM check_ins c JOIN places p ON p.id = c.place_id
              WHERE {VISIBLE_PLACE} AND c.created_at >= :from
              UNION ALL
              SELECT p.created_by, 15
              FROM places p
              WHERE {VISIBLE_PLACE} AND p.moderated_at >= :from
            ) x
            WHERE (:userId IS NULL OR x.user_id = :userId)
            GROUP BY x.user_id
            """.replace("{VISIBLE_PLACE}", VISIBLE_PLACE);

    private final NamedParameterJdbcTemplate jdbc;

    public ContributionQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Tổng điểm toàn thời gian của một người (mọi thành phố) — kể cả tài khoản bị khóa (hồ sơ vẫn hiện điểm). */
    public int totalOf(long userId) {
        List<Integer> rows = jdbc.queryForList(
                "SELECT t.points FROM (" + POINTS + ") t",
                params(BEGINNING, null).addValue("userId", userId),
                Integer.class);
        return rows.isEmpty() ? 0 : rows.getFirst();
    }

    /**
     * Bảng xếp hạng: hạng kiểu thi đấu (bằng điểm thì cùng hạng, hạng sau nhảy cóc — {@code RANK()}), cùng hạng xếp theo
     * id tăng dần cho ổn định. Bỏ tài khoản đã xóa / đang bị khóa; người 0 điểm không có mặt.
     */
    public List<Ranked> leaderboard(Instant from, String city, int limit) {
        String sql = """
                SELECT ranked.user_id, ranked.points, ranked.rnk FROM (
                  SELECT t.user_id, t.points, RANK() OVER (ORDER BY t.points DESC) AS rnk
                  FROM ({POINTS}) t JOIN users u ON u.id = t.user_id
                  WHERE u.deleted_at IS NULL AND (u.locked_until IS NULL OR u.locked_until <= UTC_TIMESTAMP(6))
                    AND t.points > 0
                ) ranked
                ORDER BY ranked.rnk, ranked.user_id
                LIMIT :limit
                """.replace("{POINTS}", POINTS);
        return jdbc.query(
                sql,
                params(from, city).addValue("userId", null).addValue("limit", limit),
                (rs, row) -> new Ranked(rs.getLong("user_id"), rs.getInt("points"), rs.getInt("rnk")));
    }

    /** Số liệu cho tiêu chí huy hiệu ({@code BadgeRule}) — cùng định nghĩa "đang hiện" với điểm. */
    public Map<String, Integer> badgeStatsOf(long userId) {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM reviews r JOIN places p ON p.id = r.place_id
                   WHERE r.user_id = :userId AND r.status = 'PUBLISHED' AND r.deleted_at IS NULL AND {VISIBLE_PLACE}) AS reviews,
                  (SELECT COUNT(DISTINCT c.place_id) FROM check_ins c JOIN places p ON p.id = c.place_id
                   WHERE c.user_id = :userId AND {VISIBLE_PLACE}) AS checkin_places,
                  (SELECT COUNT(*) FROM review_photos rp JOIN reviews r ON r.id = rp.review_id
                     JOIN places p ON p.id = r.place_id
                   WHERE r.user_id = :userId AND rp.status = 'READY' AND r.status = 'PUBLISHED'
                     AND r.deleted_at IS NULL AND {VISIBLE_PLACE}) AS photos,
                  (SELECT COUNT(*) FROM review_votes v JOIN reviews r ON r.id = v.review_id
                     JOIN places p ON p.id = r.place_id
                   WHERE r.user_id = :userId AND v.counted AND r.status = 'PUBLISHED' AND r.deleted_at IS NULL
                     AND {VISIBLE_PLACE}) AS helpful_votes
                """.replace("{VISIBLE_PLACE}", VISIBLE_PLACE);
        Map<String, Integer> stats =
                jdbc.queryForObject(sql, params(BEGINNING, null).addValue("userId", userId), (rs, row) -> {
                    Map<String, Integer> values = new HashMap<>();
                    for (String column : List.of("reviews", "checkin_places", "photos", "helpful_votes")) {
                        values.put(column, rs.getInt(column));
                    }
                    return values;
                });
        return stats == null ? Map.of() : stats;
    }

    /**
     * Cột thời gian lưu UTC; truyền {@code LocalDateTime} theo UTC để driver gửi nguyên giá trị — {@code Timestamp} bị đổi
     * theo múi giờ của JVM (máy dev / test chạy +07).
     */
    private static MapSqlParameterSource params(Instant from, String city) {
        return new MapSqlParameterSource()
                .addValue("from", LocalDateTime.ofInstant(from, ZoneOffset.UTC))
                .addValue("city", city);
    }

    public record Ranked(long userId, int points, int rank) {}
}
