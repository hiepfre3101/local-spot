package com.localspot.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.localspot.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * NFR-03: truy vấn bán kính dùng spatial index, không quét toàn bảng — {@code EXPLAIN} đúng câu SQL ứng dụng chạy
 * ({@link PlaceSearchRepositoryImpl#WITHIN_SQL}), tham số thay bằng giá trị cụ thể.
 *
 * <p>Cần bảng có dữ liệu cỡ thật: với bảng gần rỗng (CSDL test mới) MySQL quét toàn bảng kể cả khi có
 * {@code FORCE INDEX} — test chèn 300 địa điểm (vĩ độ 10–13, xa tọa độ các test khác) rồi {@code ANALYZE TABLE}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlaceSpatialIndexTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Test
    void radiusQueryUsesTheSpatialIndex() throws Exception {
        insertPlaces(300);

        String sql = PlaceSearchRepositoryImpl.WITHIN_SQL
                .replace("{condition}", "p.status = 'APPROVED'")
                .replace(":center", "'POINT(21.0285 105.8542)'")
                .replace(":box", "'" + PlaceSearchRepositoryImpl.boundingBox(21.0285, 105.8542, 2000) + "'")
                .replace(":radius", "2000")
                .replace(":limit", "20");

        List<Map<String, Object>> plan = jdbc.queryForList("EXPLAIN " + sql);

        Map<String, Object> places = plan.stream()
                .filter(row -> "p".equals(row.get("table")))
                .findFirst()
                .orElseThrow();
        assertThat(places.get("key")).as("EXPLAIN: %s", plan).isEqualTo("sx_places_location");
        assertThat(places.get("type")).isEqualTo("range");
    }

    @Test
    void boundingBoxIsInLatLngOrderAndWiderEastWestAwayFromTheEquator() {
        String box = PlaceSearchRepositoryImpl.boundingBox(21.0, 105.0, 1000);
        // (lat lng) theo trục SRID 4326 của MySQL; nới 1 % + 10 m → 1020 m ≈ 0.0091628° vĩ độ, 0.0098147° kinh độ ở vĩ
        // độ 21
        assertThat(box).startsWith("POLYGON((20.9908372 104.9901853, ");
        assertThat(box).contains("21.0091628 105.0098147");
    }

    private void insertPlaces(int count) throws Exception {
        String email = "spatial-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"LocalSpot2026","displayName":"Người thử index"}
                        """.formatted(email)));
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        String batch = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    created_at, updated_at)
                WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < ?)
                SELECT (SELECT id FROM categories WHERE slug = 'pho-bun'), ?, CONCAT('Index ', n),
                       CONCAT('spatial-index-', ?, '-', n), 'Địa chỉ thử', 'Thử Index',
                       ST_PointFromText(CONCAT('POINT(', 10 + n * 0.01, ' ', 100 + n * 0.01, ')'), 4326),
                       'APPROVED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                FROM seq
                """, count, userId, batch);
        jdbc.queryForList("ANALYZE TABLE places");
    }
}
