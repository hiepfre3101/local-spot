package com.localspot.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Số liệu thống kê: dashboard quản trị (UC33, FR-41) và thống kê địa điểm của chủ (UC26, FR-34). Chỉ đọc, đếm thẳng
 * trên bảng gốc — không có bảng tổng hợp, nên số liệu luôn khớp dữ liệu (đánh đổi: mỗi lần mở dashboard quét theo
 * khoảng ngày; ở quy mô đồ án vài nghìn dòng / bảng là đủ nhanh).
 *
 * <p><b>Ngày</b> tính theo giờ Việt Nam: cột thời gian lưu UTC, cộng 7 giờ trước khi lấy {@code DATE()}. Dùng độ lệch
 * cố định thay vì {@code CONVERT_TZ(…, 'Asia/Ho_Chi_Minh')} vì Việt Nam không có giờ mùa hè và MySQL không cần nạp bảng
 * múi giờ. Khoảng thời gian truyền vào là {@code [start, end)} UTC đã quy đổi từ ngày Việt Nam ở service.
 */
@Repository
public class StatsQueries {

    private static final String VN_DATE = "DATE(%s + INTERVAL 7 HOUR)";

    private final NamedParameterJdbcTemplate jdbc;

    public StatsQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ─── Dashboard quản trị (UC33) ─────────────────────────────────────────

    /** Tài khoản đăng ký theo ngày — kể cả tài khoản đã xóa / bị khóa sau đó: số liệu quá khứ không đổi theo thời gian. */
    public Map<LocalDate, Long> newUsersByDay(Instant start, Instant end) {
        return byDay("users", start, end);
    }

    /**
     * Review được gửi theo ngày — <b>mọi trạng thái, kể cả đã xóa sau đó</b>: đo lượng hoạt động / khối lượng việc, không
     * phải nội dung đang hiện.
     */
    public Map<LocalDate, Long> reviewsByDay(Instant start, Instant end) {
        return byDay("reviews", start, end);
    }

    public long countUsers(Instant start, Instant end) {
        return count("users", start, end);
    }

    public long countReviews(Instant start, Instant end) {
        return count("reviews", start, end);
    }

    /** Báo cáo vi phạm nhận được (mọi trạng thái). */
    public long countReports(Instant start, Instant end) {
        return count("reports", start, end);
    }

    /**
     * Review đang hiện ({@code PUBLISHED} trên địa điểm {@code APPROVED}, chưa xóa) viết trong khoảng, gom theo thành phố
     * — "khu vực nhiều đánh giá" của AdminDashboard. Nhiều trước, cùng số theo tên.
     */
    public List<CityCount> visibleReviewsByCity(Instant start, Instant end, int limit) {
        String sql = """
                SELECT p.city, COUNT(*) AS cnt
                FROM reviews r JOIN places p ON p.id = r.place_id
                WHERE {VISIBLE_REVIEW} AND r.created_at >= :start AND r.created_at < :end
                GROUP BY p.city
                ORDER BY cnt DESC, p.city
                LIMIT :limit
                """.replace("{VISIBLE_REVIEW}", VISIBLE_REVIEW);
        return jdbc.query(
                sql,
                range(start, end).addValue("limit", limit),
                (rs, row) -> new CityCount(rs.getString("city"), rs.getLong("cnt")));
    }

    /**
     * Địa điểm "hot" (FR-41, supervisor chốt 2026-10-17): nhiều review <b>đang hiện</b> mới viết trong khoảng nhất — hoạt
     * động thật của cộng đồng, khó thổi phồng hơn lượt xem (review ẩn / chờ duyệt / bị từ chối không tính). Cùng số thì
     * điểm Bayesian cao hơn trước, rồi id.
     */
    public List<PlaceCount> hotPlaces(Instant start, Instant end, int limit) {
        String sql = """
                SELECT p.id, COUNT(*) AS cnt
                FROM reviews r JOIN places p ON p.id = r.place_id
                WHERE {VISIBLE_REVIEW} AND r.created_at >= :start AND r.created_at < :end
                GROUP BY p.id, p.bayesian_score
                ORDER BY cnt DESC, p.bayesian_score DESC, p.id
                LIMIT :limit
                """.replace("{VISIBLE_REVIEW}", VISIBLE_REVIEW);
        return jdbc.query(
                sql,
                range(start, end).addValue("limit", limit),
                (rs, row) -> new PlaceCount(rs.getLong("id"), rs.getLong("cnt")));
    }

    // ─── Thống kê địa điểm của chủ (UC26) ──────────────────────────────────

    /** Lượt xem theo ngày — {@code place_views} đã gộp sẵn theo ngày Việt Nam (khóa chính {@code (place_id, view_date)}). */
    public Map<LocalDate, Long> viewsByDay(long placeId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT view_date AS d, view_count AS cnt FROM place_views
                WHERE place_id = :placeId AND view_date BETWEEN :from AND :to
                """;
        return days(sql, dates(placeId, from, to));
    }

    /** Check-in theo ngày — {@code checkin_date} là ngày Việt Nam (luật 1 lần / ngày). */
    public Map<LocalDate, Long> checkinsByDay(long placeId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT checkin_date AS d, COUNT(*) AS cnt FROM check_ins
                WHERE place_id = :placeId AND checkin_date BETWEEN :from AND :to
                GROUP BY checkin_date
                """;
        return days(sql, dates(placeId, from, to));
    }

    public record CityCount(String city, long count) {}

    public record PlaceCount(long placeId, long count) {}

    // ─── Hỗ trợ ─────────────────────────────────────────────────────────────

    private static final String VISIBLE_REVIEW =
            "r.status = 'PUBLISHED' AND r.deleted_at IS NULL" + " AND p.status = 'APPROVED' AND p.deleted_at IS NULL";

    /** {@code table} là hằng trong lớp này — không bao giờ lấy từ request. */
    private Map<LocalDate, Long> byDay(String table, Instant start, Instant end) {
        String sql = "SELECT " + VN_DATE.formatted("created_at") + " AS d, COUNT(*) AS cnt FROM " + table
                + " WHERE created_at >= :start AND created_at < :end GROUP BY d";
        return days(sql, range(start, end));
    }

    private long count(String table, Instant start, Instant end) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE created_at >= :start AND created_at < :end",
                range(start, end),
                Long.class);
        return count == null ? 0 : count;
    }

    private Map<LocalDate, Long> days(String sql, MapSqlParameterSource params) {
        Map<LocalDate, Long> byDay = new TreeMap<>();
        jdbc.query(sql, params, rs -> {
            byDay.put(rs.getObject("d", LocalDate.class), rs.getLong("cnt"));
        });
        return byDay;
    }

    /** UTC {@code LocalDateTime} như {@link ContributionQueries} — {@code Timestamp} bị đổi theo múi giờ JVM. */
    private static MapSqlParameterSource range(Instant start, Instant end) {
        return new MapSqlParameterSource()
                .addValue("start", LocalDateTime.ofInstant(start, ZoneOffset.UTC))
                .addValue("end", LocalDateTime.ofInstant(end, ZoneOffset.UTC));
    }

    private static MapSqlParameterSource dates(long placeId, LocalDate from, LocalDate to) {
        return new MapSqlParameterSource()
                .addValue("placeId", placeId)
                .addValue("from", from)
                .addValue("to", to);
    }
}
