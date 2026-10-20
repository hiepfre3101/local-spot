package com.localspot.repository;

import com.localspot.entity.ModerationItemType;
import com.localspot.entity.ReportTargetType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Hàng chờ gộp {@code /moderation/queue} (UC27–UC30): một {@code UNION ALL} trên bốn nguồn, mỗi nhánh dùng <b>đúng điều
 * kiện</b> của hàng chờ riêng loại đó, để hai màn không bao giờ lệch nhau:
 *
 * <pre>
 *   PLACE   places PENDING chưa xóa                                  ({@code PlaceRepository#findByStatusOldestFirst})
 *   REVIEW  reviews PENDING chưa xóa trên địa điểm APPROVED chưa xóa  ({@code ReviewRepository#findModerationQueue})
 *   REPORT  nhóm báo cáo OPEN theo đối tượng, mốc = báo cáo đầu tiên  ({@code ReportRepository#findOpenGroups})
 *   CLAIM   place_claims PENDING                                     ({@code PlaceClaimRepository#findPendingOldestFirst})
 * </pre>
 *
 * Mỗi nhánh tự đặt tên cột: nhánh nào đứng đầu {@code UNION} là tùy quyền người gọi.
 *
 * <p>Thứ tự cố định {@code (submitted_at, type, sort_id)} tăng dần, keyset trên bộ ba này (không OFFSET). Chỉ ghép nhánh
 * của các loại người gọi có quyền — nhánh không được ghép thì không bị quét. Hàng chờ cỡ vài trăm mục nên sắp xếp
 * trên kết quả gộp là đủ; mỗi nhánh vẫn lọc bằng index {@code (status, …)} của bảng mình.
 */
@Repository
public class ModerationQueueQueries {

    private static final Map<ModerationItemType, String> BRANCHES = new EnumMap<>(Map.of(
            ModerationItemType.PLACE,
            """
            SELECT 'PLACE' AS type, 0 AS type_rank, p.id AS item_id, NULL AS report_target_type, p.id AS sort_id,
                   p.created_at AS submitted_at, p.created_by AS submitter_id, 0 AS report_count, FALSE AS ip_flagged
            FROM places p
            WHERE p.status = 'PENDING' AND p.deleted_at IS NULL
            """,
            ModerationItemType.REVIEW,
            """
            SELECT 'REVIEW' AS type, 1 AS type_rank, r.id AS item_id, NULL AS report_target_type, r.id AS sort_id,
                   r.created_at AS submitted_at, r.user_id AS submitter_id, 0 AS report_count, r.ip_flagged AS ip_flagged
            FROM reviews r JOIN places p ON p.id = r.place_id
            WHERE r.status = 'PENDING' AND r.deleted_at IS NULL AND p.status = 'APPROVED' AND p.deleted_at IS NULL
            """,
            ModerationItemType.REPORT,
            """
            SELECT 'REPORT' AS type, 2 AS type_rank, g.target_id AS item_id, g.target_type AS report_target_type, g.first_id AS sort_id,
                   g.first_at AS submitted_at, f.reporter_id AS submitter_id, g.report_count AS report_count, FALSE AS ip_flagged
            FROM (SELECT target_type, target_id, MIN(id) AS first_id, MIN(created_at) AS first_at,
                         COUNT(*) AS report_count
                  FROM reports WHERE status = 'OPEN' GROUP BY target_type, target_id) g
            JOIN reports f ON f.id = g.first_id
            """,
            ModerationItemType.CLAIM,
            """
            SELECT 'CLAIM' AS type, 3 AS type_rank, c.id AS item_id, NULL AS report_target_type, c.id AS sort_id,
                   c.created_at AS submitted_at, c.user_id AS submitter_id, 0 AS report_count, FALSE AS ip_flagged
            FROM place_claims c
            WHERE c.status = 'PENDING'
            """));

    /** Mục đứng sau {@code :after*} theo thứ tự hàng chờ — so sánh từng phần thay vì so bộ (MySQL so bộ kém ổn định). */
    private static final String AFTER = """
            (q.submitted_at > :afterAt
             OR (q.submitted_at = :afterAt AND (q.type_rank > :afterRank
                                                OR (q.type_rank = :afterRank AND q.sort_id > :afterSortId))))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ModerationQueueQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Một trang, cũ nhất trước. {@code after = null} = trang đầu. {@code types} rỗng → rỗng. */
    public List<ModerationQueueRow> page(Collection<ModerationItemType> types, ModerationQueueKey after, int limit) {
        if (types.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT * FROM (" + union(types) + ") q"
                + (after == null ? "" : " WHERE " + AFTER)
                + " ORDER BY q.submitted_at, q.type_rank, q.sort_id LIMIT :limit";
        return jdbc.query(sql, params(after).addValue("limit", limit), ModerationQueueQueries::row);
    }

    /** Số mục đứng trước hoặc đúng tại {@code key} — để đánh số thứ tự ({@code position}) cho trang sau. */
    public long countUpTo(Collection<ModerationItemType> types, ModerationQueueKey key) {
        if (types.isEmpty()) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM (" + union(types) + ") q WHERE NOT " + AFTER;
        Long count = jdbc.queryForObject(sql, params(key), Long.class);
        return count == null ? 0 : count;
    }

    /** Số việc tồn và thời điểm gửi của mục cũ nhất, theo loại — loại không còn việc không có trong kết quả. */
    public Map<ModerationItemType, Pending> pending(Collection<ModerationItemType> types) {
        if (types.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT q.type, COUNT(*) AS cnt, MIN(q.submitted_at) AS oldest FROM (" + union(types)
                + ") q GROUP BY q.type";
        return jdbc
                .query(
                        sql,
                        (rs, row) -> new Pending(
                                ModerationItemType.valueOf(rs.getString("type")),
                                rs.getLong("cnt"),
                                instant(rs, "oldest")))
                .stream()
                .collect(Collectors.toMap(
                        Pending::type, p -> p, (a, b) -> a, () -> new EnumMap<>(ModerationItemType.class)));
    }

    public record Pending(ModerationItemType type, long count, Instant oldestSubmittedAt) {}

    private static String union(Collection<ModerationItemType> types) {
        return types.stream().distinct().sorted().map(BRANCHES::get).collect(Collectors.joining(" UNION ALL "));
    }

    /**
     * Cột thời gian lưu UTC; truyền {@code LocalDateTime} theo UTC để driver gửi nguyên giá trị (như
     * {@link ContributionQueries}).
     */
    private static MapSqlParameterSource params(ModerationQueueKey key) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (key != null) {
            params.addValue("afterAt", LocalDateTime.ofInstant(key.submittedAt(), ZoneOffset.UTC))
                    .addValue("afterRank", key.type().ordinal())
                    .addValue("afterSortId", key.sortId());
        }
        return params;
    }

    private static ModerationQueueRow row(ResultSet rs, int rowNum) throws SQLException {
        String reportTargetType = rs.getString("report_target_type");
        return new ModerationQueueRow(
                ModerationItemType.valueOf(rs.getString("type")),
                rs.getLong("item_id"),
                reportTargetType == null ? null : ReportTargetType.valueOf(reportTargetType),
                rs.getLong("sort_id"),
                instant(rs, "submitted_at"),
                rs.getLong("submitter_id"),
                rs.getInt("report_count"),
                rs.getBoolean("ip_flagged"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
