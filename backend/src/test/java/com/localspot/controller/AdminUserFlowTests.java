package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * RBAC qua HTTP trên MySQL thật: {@code @PreAuthorize} theo permission (401 khách / 403 thiếu quyền) và luật UC31 đã
 * chốt 2026-10-03 (USER bắt buộc, OWNER chỉ qua UC30, admin không tự khóa / tự gỡ ADMIN). Như {@link AuthFlowTests}:
 * không @Transactional, mỗi test tạo tài khoản riêng.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String USERS = "/api/v1/admin/users";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ─── Phân quyền ───────────────────────────────────────────────────────────

    @Test
    void anonymousGets401OnAdminEndpoints() throws Exception {
        mvc.perform(get(USERS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(put(USERS + "/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"USER\"]"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberGets403OnEveryAdminUserEndpoint() throws Exception {
        Account member = account("USER");
        Account target = account("USER");

        for (MockHttpServletRequestBuilder request : List.of(
                get(USERS),
                lockRequest(target.id(), Instant.now().plus(1, ChronoUnit.DAYS)),
                delete(USERS + "/" + target.id() + "/lock"),
                rolesRequest(target.id(), "USER", "ADMIN"))) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, member.bearer()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        assertThat(rolesOf(target.id())).containsExactly("USER");
    }

    @Test
    void moderatorCannotLockAccounts() throws Exception {
        // UC29 3b: khóa tài khoản là quyền riêng của admin (Q2)
        Account moderator = account("USER", "MODERATOR");
        Account target = account("USER");

        mvc.perform(lockRequest(target.id(), Instant.now().plus(1, ChronoUnit.DAYS))
                        .header(HttpHeaders.AUTHORIZATION, moderator.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, moderator.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void grantedRoleTakesEffectWithoutNewToken() throws Exception {
        Account admin = account("USER", "ADMIN");
        Account staff = account("USER");
        mvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, staff.bearer()))
                .andExpect(status().isForbidden());

        perform(admin, rolesRequest(staff.id(), "USER", "ADMIN")).andExpect(status().isNoContent());

        // Cùng access token cũ — quyền nạp từ CSDL mỗi request nên có hiệu lực ngay
        mvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, staff.bearer()))
                .andExpect(status().isOk());

        perform(admin, rolesRequest(staff.id(), "USER")).andExpect(status().isNoContent());
        mvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, staff.bearer()))
                .andExpect(status().isForbidden());
    }

    // ─── Tìm kiếm ────────────────────────────────────────────────────────────

    @Test
    void searchesAccentInsensitivelyWithKeysetPaging() throws Exception {
        Account admin = account("USER", "ADMIN");
        String tag = tag();
        Account oldest = accountNamed("Quản Trị " + tag, "USER");
        Account middle = accountNamed("Quản Trị " + tag, "USER");
        Account newest = accountNamed("Quản Trị " + tag, "USER", "MODERATOR");

        MvcResult first = perform(
                        admin, get(USERS).param("q", "quan tri " + tag).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(newest.id()))
                .andExpect(jsonPath("$.items[0].roles[0]").value("MODERATOR"))
                .andExpect(jsonPath("$.items[0].roles[1]").value("USER"))
                .andExpect(jsonPath("$.items[0].email").value(newest.email()))
                .andExpect(jsonPath("$.items[0].trustScore").isNumber())
                .andExpect(jsonPath("$.items[0].lockedUntil").isEmpty())
                .andExpect(jsonPath("$.items[1].id").value(middle.id()))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn();
        assertThat(first.getResponse().getContentAsString()).doesNotContain("password");

        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor");
        perform(
                        admin,
                        get(USERS)
                                .param("q", "quan tri " + tag)
                                .param("limit", "2")
                                .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(oldest.id()))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void filtersByRoleAndLockState() throws Exception {
        Account admin = account("USER", "ADMIN");
        String tag = tag();
        Account moderator = accountNamed("Lọc " + tag, "USER", "MODERATOR");
        Account locked = accountNamed("Lọc " + tag, "USER");
        perform(admin, lockRequest(locked.id(), Instant.now().plus(1, ChronoUnit.DAYS)))
                .andExpect(status().isNoContent());

        perform(admin, get(USERS).param("q", tag).param("role", "MODERATOR"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(moderator.id()));
        perform(admin, get(USERS).param("q", tag).param("locked", "true"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(locked.id()))
                .andExpect(jsonPath("$.items[0].lockedUntil").isString());
        perform(admin, get(USERS).param("q", tag).param("locked", "false"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(moderator.id()));
    }

    @Test
    void treatsLikeWildcardsLiterally() throws Exception {
        Account admin = account("USER", "ADMIN");
        String tag = tag();
        Account discount = accountNamed("Giảm 50% " + tag, "USER");

        // Không thoát thì "%_%" khớp mọi người dùng; thoát rồi thì chỉ khớp tên chứa đúng chuỗi "%_%" — không có ai
        perform(admin, get(USERS).param("q", "%_%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        perform(admin, get(USERS).param("q", "50% " + tag))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(discount.id()));
    }

    @Test
    void rejectsBadQueryParameters() throws Exception {
        Account admin = account("USER", "ADMIN");

        perform(admin, get(USERS).param("cursor", "khong-phai-cursor"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        perform(admin, get(USERS).param("limit", "51"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
        perform(admin, get(USERS).param("role", "SUPERADMIN"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("role"));
    }

    // ─── Khóa / mở khóa ──────────────────────────────────────────────────────

    @Test
    void lockingRevokesSessionsAndUnlockRestoresLogin() throws Exception {
        Account admin = account("USER", "ADMIN");
        Account target = account("USER");
        String refreshToken = refreshTokenOf(login(target.email()));

        perform(admin, lockRequest(target.id(), Instant.now().plus(3, ChronoUnit.DAYS)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, target.bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT lock_reason FROM users WHERE id = ?", String.class, target.id()))
                .isEqualTo("Spam đánh giá");

        perform(admin, delete(USERS + "/" + target.id() + "/lock")).andExpect(status().isNoContent());
        perform(admin, delete(USERS + "/" + target.id() + "/lock")).andExpect(status().isNoContent());
        login(target.email()).andExpect(status().isOk());
    }

    @Test
    void adminActionsAreWrittenToActivityLog() throws Exception {
        // FR-42: mọi thao tác ghi của admin vào activity_log; thao tác bị từ chối thì không
        Account admin = account("USER", "ADMIN");
        Account target = account("USER");

        perform(admin, lockRequest(target.id(), Instant.now().plus(1, ChronoUnit.DAYS)))
                .andExpect(status().isNoContent());
        perform(admin, delete(USERS + "/" + target.id() + "/lock")).andExpect(status().isNoContent());
        perform(admin, rolesRequest(target.id(), "USER", "MODERATOR")).andExpect(status().isNoContent());
        perform(admin, rolesRequest(target.id(), "MODERATOR")).andExpect(status().isUnprocessableContent());

        List<Map<String, Object>> logs = jdbc.queryForList(
                "SELECT actor_id, action, metadata, ip_address FROM activity_log"
                        + " WHERE target_type = 'USER' AND target_id = ? ORDER BY id",
                target.id());
        assertThat(logs)
                .extracting(row -> row.get("action"))
                .containsExactly("USER_LOCK", "USER_UNLOCK", "USER_ASSIGN_ROLES");
        assertThat(logs).allSatisfy(row -> {
            assertThat(((Number) row.get("actor_id")).longValue()).isEqualTo(admin.id());
            assertThat(row.get("ip_address")).isEqualTo("127.0.0.1");
        });
        assertThat(JsonPath.<String>read((String) logs.get(0).get("metadata"), "$.reason"))
                .isEqualTo("Spam đánh giá");
        String roles = (String) logs.get(2).get("metadata");
        assertThat(JsonPath.<List<String>>read(roles, "$.before")).containsExactly("USER");
        assertThat(JsonPath.<List<String>>read(roles, "$.after")).containsExactly("MODERATOR", "USER");
    }

    @Test
    void adminCannotLockThemselvesButCanLockAnotherAdmin() throws Exception {
        Account admin = account("USER", "ADMIN");
        Account otherAdmin = account("USER", "ADMIN");

        perform(admin, lockRequest(admin.id(), Instant.now().plus(1, ChronoUnit.DAYS)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));
        perform(admin, lockRequest(otherAdmin.id(), Instant.now().plus(1, ChronoUnit.DAYS)))
                .andExpect(status().isNoContent());
    }

    @Test
    void validatesLockRequestAndUnknownUser() throws Exception {
        Account admin = account("USER", "ADMIN");
        Account target = account("USER");

        perform(admin, lockRequest(target.id(), Instant.now().minus(1, ChronoUnit.HOURS)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[?(@.field == 'until')]").exists());
        perform(
                        admin,
                        post(USERS + "/" + target.id() + "/lock")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"until\":\"%s\",\"reason\":\" \"}"
                                        .formatted(Instant.now().plus(1, ChronoUnit.DAYS))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[?(@.field == 'reason')]").exists());
        perform(admin, lockRequest(Long.MAX_VALUE, Instant.now().plus(1, ChronoUnit.DAYS)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ─── Gán vai trò ─────────────────────────────────────────────────────────

    @Test
    void rejectsRoleListsBreakingInvariants() throws Exception {
        Account admin = account("USER", "ADMIN");
        Account target = account("USER");

        perform(admin, rolesRequest(target.id(), "MODERATOR"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ROLE_USER_REQUIRED"));
        perform(admin, rolesRequest(target.id()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        perform(admin, rolesRequest(target.id(), "USER", "SUPERADMIN"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        perform(admin, rolesRequest(Long.MAX_VALUE, "USER")).andExpect(status().isNotFound());
        assertThat(rolesOf(target.id())).containsExactly("USER");
    }

    @Test
    void ownerRoleIsOnlyManagedThroughClaims() throws Exception {
        Account admin = account("USER", "ADMIN");
        Account member = account("USER");
        Account owner = account("USER", "OWNER");

        perform(admin, rolesRequest(member.id(), "USER", "OWNER"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OWNER_ROLE_MANAGED_BY_CLAIM"));
        perform(admin, rolesRequest(owner.id(), "USER"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OWNER_ROLE_MANAGED_BY_CLAIM"));

        // Giữ nguyên OWNER, chỉ đổi role khác → hợp lệ
        perform(admin, rolesRequest(owner.id(), "USER", "OWNER", "MODERATOR")).andExpect(status().isNoContent());
        assertThat(rolesOf(owner.id())).containsExactly("MODERATOR", "OWNER", "USER");
    }

    @Test
    void adminCannotDropOwnAdminRoleButCanDemoteAnotherAdmin() throws Exception {
        Account admin = account("USER", "ADMIN");
        Account otherAdmin = account("USER", "ADMIN");

        perform(admin, rolesRequest(admin.id(), "USER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));
        // Tự sửa role khác của mình mà vẫn giữ ADMIN thì được
        perform(admin, rolesRequest(admin.id(), "USER", "ADMIN", "MODERATOR")).andExpect(status().isNoContent());

        perform(admin, rolesRequest(otherAdmin.id(), "USER")).andExpect(status().isNoContent());
        assertThat(rolesOf(otherAdmin.id())).containsExactly("USER");
    }

    // ─── Hỗ trợ ──────────────────────────────────────────────────────────────

    private record Account(long id, String email, String accessToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    private Account account(String... roles) throws Exception {
        return accountNamed("Người thử", roles);
    }

    /** Đăng ký qua API (role USER mặc định), gán thêm role bằng SQL rồi đăng nhập. */
    private Account accountNamed(String displayName, String... roles) throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"%s"}
                                """.formatted(email, PASSWORD, displayName)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        for (String role : roles) {
            jdbc.update(
                    "INSERT IGNORE INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = ?",
                    id,
                    role);
        }
        String accessToken =
                JsonPath.read(login(email).andReturn().getResponse().getContentAsString(), "$.accessToken");
        return new Account(id, email, accessToken);
    }

    private ResultActions login(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, PASSWORD)));
    }

    private ResultActions perform(Account actor, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, actor.bearer()));
    }

    private static MockHttpServletRequestBuilder lockRequest(long userId, Instant until) {
        return post(USERS + "/" + userId + "/lock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"until\":\"%s\",\"reason\":\"Spam đánh giá\"}".formatted(until));
    }

    private static MockHttpServletRequestBuilder rolesRequest(long userId, String... roles) {
        String body = "["
                + String.join(
                        ",", List.of(roles).stream().map(r -> "\"" + r + "\"").toList()) + "]";
        return put(USERS + "/" + userId + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private List<String> rolesOf(long userId) {
        return jdbc.queryForList(
                "SELECT r.name FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ? ORDER BY r.name",
                String.class,
                userId);
    }

    private static String refreshTokenOf(ResultActions login) throws Exception {
        String setCookie = login.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    /** Chuỗi chữ thường duy nhất để cô lập kết quả tìm kiếm khỏi dữ liệu của test khác. */
    private static String tag() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
