package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Checklist E4 qua HTTP trên MySQL thật: tìm quanh vị trí (FR-11, U8) và cảnh báo nghi trùng (U7). Mỗi test đặt địa điểm
 * quanh một tâm ngẫu nhiên riêng (vĩ độ 40–45, kinh độ 120–125 — xa tọa độ của các test khác) để không lẫn kết quả.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NearbyFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String NEARBY = "/api/v1/places/nearby";
    private static final String DUPLICATES = "/api/v1/places/duplicates";

    /** Mét trên một độ vĩ theo bán kính Trái Đất MySQL dùng trong ST_Distance_Sphere (6 370 986 m). */
    private static final double METERS_PER_DEGREE = 6_370_986 * Math.PI / 180;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ─── Tìm quanh vị trí (FR-11) ───────────────────────────────────────────

    @Test
    void returnsApprovedPlacesWithinRadiusNearestFirst() throws Exception {
        Account member = account("USER");
        Center center = center();
        long at100 = place(member, "Gần 100", center.north(100), "APPROVED");
        long at500 = place(member, "Gần 500", center.east(500), "APPROVED");
        long at1500 = place(member, "Gần 1500", center.north(-1500), "APPROVED");
        long at2500 = place(member, "Xa 2500", center.east(-2500), "APPROVED");
        place(member, "Chờ duyệt 200", center.north(200), "PENDING");
        place(member, "Bị từ chối 300", center.east(300), "REJECTED");
        long deleted = place(member, "Đã xóa 400", center.north(400), "APPROVED");
        jdbc.update("UPDATE places SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);

        // Mặc định 2 km (U8)
        MvcResult result = mvc.perform(get(NEARBY).param("lat", center.lat()).param("lng", center.lng()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(ids(result)).containsExactly(at100, at500, at1500);
        List<Integer> distances = JsonPath.read(result.getResponse().getContentAsString(), "$[*].distanceM");
        assertThat(distances.get(0)).isCloseTo(100, within(2));
        assertThat(distances.get(1)).isCloseTo(500, within(5));
        assertThat(distances.get(2)).isCloseTo(1500, within(15));

        assertThat(ids(mvc.perform(get(NEARBY)
                                .param("lat", center.lat())
                                .param("lng", center.lng())
                                .param("radius", "3000"))
                        .andReturn()))
                .containsExactly(at100, at500, at1500, at2500);
        assertThat(ids(mvc.perform(get(NEARBY)
                                .param("lat", center.lat())
                                .param("lng", center.lng())
                                .param("limit", "2"))
                        .andReturn()))
                .containsExactly(at100, at500);
    }

    @Test
    void radiusEdgeIsExactInEveryDirection() throws Exception {
        Account member = account("USER");
        Center center = center();
        // Hộp lọc thô phải đủ rộng theo cả bốn hướng (kinh độ co lại theo cos vĩ độ) — điểm sát mép trong vẫn lọt
        List<Long> inside = List.of(
                place(member, "Trong bắc", center.north(985), "APPROVED"),
                place(member, "Trong nam", center.north(-985), "APPROVED"),
                place(member, "Trong đông", center.east(985), "APPROVED"),
                place(member, "Trong tây", center.east(-985), "APPROVED"));
        place(member, "Ngoài bắc", center.north(1015), "APPROVED");
        place(member, "Ngoài đông", center.east(1015), "APPROVED");

        MvcResult result = mvc.perform(get(NEARBY)
                        .param("lat", center.lat())
                        .param("lng", center.lng())
                        .param("radius", "1000"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(ids(result)).containsExactlyInAnyOrderElementsOf(inside);
    }

    @Test
    void categoryFilterIncludesSubcategories() throws Exception {
        Account member = account("USER");
        Center center = center();
        long pho = place(member, "Quán phở", center.north(100), "APPROVED"); // pho-bun ⊂ quan-an
        long cafe = place(member, "Quán cà phê", center.north(200), "APPROVED", categoryId("ca-phe"));

        assertThat(ids(mvc.perform(get(NEARBY)
                                .param("lat", center.lat())
                                .param("lng", center.lng())
                                .param("categoryId", String.valueOf(categoryId("quan-an"))))
                        .andReturn()))
                .containsExactly(pho);
        assertThat(ids(mvc.perform(get(NEARBY)
                                .param("lat", center.lat())
                                .param("lng", center.lng())
                                .param("categoryId", String.valueOf(categoryId("ca-phe"))))
                        .andReturn()))
                .containsExactly(cafe);
    }

    @Test
    void rejectsOutOfRangeParameters() throws Exception {
        mvc.perform(get(NEARBY).param("lat", "21").param("lng", "105").param("radius", "50"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("radius"))
                .andExpect(jsonPath("$.errors[0].message").value("Bán kính từ 100 m đến 20 km."));
        mvc.perform(get(NEARBY).param("lat", "21").param("lng", "105").param("radius", "20001"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get(NEARBY).param("lat", "91").param("lng", "105"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("lat"));
        mvc.perform(get(NEARBY).param("lat", "21").param("lng", "105").param("limit", "51"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get(NEARBY).param("lat", "21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // ─── Nghi trùng (U7) ────────────────────────────────────────────────────

    @Test
    void warnsAboutSimilarNamesWithinFiftyMetres() throws Exception {
        Account member = account("USER");
        Account other = account("USER");
        Center center = center();
        long similar = place(other, "Cà phê Giảng", center.north(20), "APPROVED");
        place(other, "Giảng Coffee", center.north(80), "APPROVED"); // giống tên nhưng ngoài 50 m
        place(other, "Bún chả Hương Liên", center.east(10), "APPROVED"); // trong 50 m nhưng khác tên
        long myPending = place(member, "Giảng cafe", center.east(-30), "PENDING");
        place(other, "Cafe Giảng", center.east(15), "PENDING"); // đề xuất chờ của người khác — riêng tư

        MvcResult result = perform(
                        member,
                        get(DUPLICATES)
                                .param("name", "Giảng Coffee")
                                .param("lat", center.lat())
                                .param("lng", center.lng()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(ids(result)).containsExactly(similar, myPending);
        List<Integer> distances = JsonPath.read(result.getResponse().getContentAsString(), "$[*].distanceM");
        assertThat(distances.get(0)).isCloseTo(20, within(1));

        perform(
                        member,
                        get(DUPLICATES)
                                .param("name", "Phở Lý")
                                .param("lat", center.lat())
                                .param("lng", center.lng()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** Hai người dùng đề xuất cùng một quán: họ không thấy đề xuất chờ của nhau, kiểm duyệt viên thì thấy (2026-10-07). */
    @Test
    void moderatorSeesDuplicateProposalsFromDifferentUsers() throws Exception {
        Account alice = account("USER");
        Account bob = account("USER");
        Account moderator = account("USER", "MODERATOR");
        Center center = center();
        long aliceProposal = place(alice, "Cà phê Giảng", center.north(0), "PENDING");
        long bobProposal = place(bob, "Giảng Coffee", center.north(20), "PENDING");
        long approved = place(bob, "Giang Cafe", center.east(30), "APPROVED");
        place(bob, "Bún chả Hương Liên", center.east(10), "PENDING"); // khác tên
        place(bob, "Cafe Giảng", center.north(90), "APPROVED"); // giống tên nhưng ngoài 50 m

        // Người đề xuất không thấy đề xuất chờ của người kia (giữ quyền riêng tư như E1)
        assertThat(ids(perform(
                                bob,
                                get(DUPLICATES)
                                        .param("name", "Giảng Coffee")
                                        .param("lat", center.lat())
                                        .param("lng", center.lng()))
                        .andReturn()))
                .doesNotContain(aliceProposal);

        // Kiểm duyệt viên: mỗi đề xuất liệt kê đề xuất kia + địa điểm đã duyệt, gần nhất trước
        assertThat(duplicatesInQueue(moderator, aliceProposal)).containsExactly(bobProposal, approved);
        assertThat(duplicatesInQueue(moderator, bobProposal)).containsExactly(aliceProposal, approved);

        // Lịch sử (đã duyệt) không tính nghi trùng
        String history = perform(moderator, get("/api/v1/moderation/places").param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<List<?>> historyDuplicates = JsonPath.read(history, "$.items[*].possibleDuplicates");
        assertThat(historyDuplicates)
                .isNotEmpty()
                .allSatisfy(list -> assertThat(list).isEmpty());
    }

    @Test
    void duplicateCheckRequiresLoginAndAName() throws Exception {
        mvc.perform(get(DUPLICATES).param("name", "x").param("lat", "21").param("lng", "105"))
                .andExpect(status().isUnauthorized());
        perform(
                        account("USER"),
                        get(DUPLICATES).param("name", " ").param("lat", "21").param("lng", "105"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    // ─── Tiện ích ────────────────────────────────────────────────────────────

    /** Tâm ngẫu nhiên; {@link #north} / {@link #east} dời đi đúng số mét theo cầu MySQL dùng. */
    private record Center(double latitude, double longitude) {
        String lat() {
            return format(latitude);
        }

        String lng() {
            return format(longitude);
        }

        double[] north(double meters) {
            return new double[] {latitude + meters / METERS_PER_DEGREE, longitude};
        }

        double[] east(double meters) {
            return new double[] {latitude, longitude + meters / (METERS_PER_DEGREE * Math.cos(Math.toRadians(latitude)))
            };
        }
    }

    private static Center center() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new Center(40 + random.nextDouble() * 5, 120 + random.nextDouble() * 5);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.7f", value);
    }

    private record Account(long id, String accessToken) {}

    private Account account(String... roles) throws Exception {
        String email = "nearby-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử quanh đây"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        for (String role : roles) {
            jdbc.update(
                    "INSERT IGNORE INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = ?",
                    id,
                    role);
        }
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new Account(id, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    /** Duyệt qua các trang của hàng chờ PENDING tới khi gặp {@code placeId}, trả id các địa điểm nghi trùng của nó. */
    private List<Long> duplicatesInQueue(Account moderator, long placeId) throws Exception {
        String cursor = null;
        do {
            var request = get("/api/v1/moderation/places").param("limit", "50");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            String page = perform(moderator, request)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            List<Number> found =
                    JsonPath.read(page, "$.items[?(@.place.id == " + placeId + ")].possibleDuplicates[*].id");
            List<Number> present = JsonPath.read(page, "$.items[?(@.place.id == " + placeId + ")].place.id");
            if (!present.isEmpty()) {
                return found.stream().map(Number::longValue).toList();
            }
            cursor = JsonPath.read(page, "$.nextCursor");
        } while (cursor != null);
        throw new AssertionError("Không thấy địa điểm " + placeId + " trong hàng chờ");
    }

    private long place(Account proposer, String name, double[] latLng, String status) throws Exception {
        return place(proposer, name, latLng, status, categoryId("pho-bun"));
    }

    /** Đề xuất qua API (đi đúng luồng tạo + lưu tọa độ), rồi đặt trạng thái trực tiếp — không cần qua hàng chờ duyệt. */
    private long place(Account proposer, String name, double[] latLng, String status, long categoryId)
            throws Exception {
        String json = """
                {"name": "%s", "categoryId": %d, "address": "1 Phố Thử", "city": "Thử Quanh Đây",
                 "location": {"lat": %s, "lng": %s}}
                """.formatted(name, categoryId, format(latLng[0]), format(latLng[1]));
        MvcResult result = perform(
                        proposer,
                        multipart("/api/v1/places")
                                .file(new MockMultipartFile(
                                        "place",
                                        "",
                                        MediaType.APPLICATION_JSON_VALUE,
                                        json.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        jdbc.update("UPDATE places SET status = ? WHERE id = ?", status, id);
        return id;
    }

    private long categoryId(String slug) {
        Long id = jdbc.queryForObject("SELECT id FROM categories WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    private static List<Long> ids(MvcResult result) throws Exception {
        List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}
