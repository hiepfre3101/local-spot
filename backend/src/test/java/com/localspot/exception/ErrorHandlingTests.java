package com.localspot.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.entity.Place;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.constraints.Min;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Định dạng lỗi chung (D7): mọi nhánh của {@link GlobalExceptionHandler} trả {@code application/problem+json} có
 * {@code code}. Lỗi Spring MVC thử qua endpoint thật; lỗi optimistic lock / CSDL / 500 thử qua {@link Probe} chỉ có
 * trong test. Lỗi CSDL dùng bảng tạm ({@code TEMPORARY TABLE}) hoặc lệnh bị chặn hoàn toàn — không ghi dữ liệu thật.
 */
@Import({TestcontainersConfiguration.class, ErrorHandlingTests.ProbeConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ErrorHandlingTests {

    private static final String PROBE = "/api/v1/test-errors";

    @Autowired
    private MockMvc mvc;

    // ─── 400 / 404 / 405 / 415 / 422 từ Spring MVC ──────────────────────────

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Yêu cầu không đúng định dạng."));
    }

    @Test
    void wrongOrMissingParameterIs400() throws Exception {
        authed(get(PROBE + "/typed").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        authed(get(PROBE + "/typed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void parameterConstraintIs422WithFieldError() throws Exception {
        authed(get(PROBE + "/typed").param("n", "0"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("n"));
    }

    @Test
    void unknownRouteIs404() throws Exception {
        authed(get("/api/v1/khong-ton-tai"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wrongMethodIs405() throws Exception {
        authed(delete("/api/v1/me"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("email=a"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // ─── 409 / 422 từ CSDL ──────────────────────────────────────────────────

    @Test
    void optimisticLockIs409() throws Exception {
        authed(get(PROBE + "/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void duplicateKeyReachingDatabaseIs409() throws Exception {
        authed(get(PROBE + "/duplicate-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void checkConstraintViolationIs422NotA500() throws Exception {
        // MySQL 3819 → Spring UncategorizedSQLException (không phải DataIntegrityViolationException) — phát hiện D1
        authed(get(PROBE + "/check-violation"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void missingForeignKeyParentIs422() throws Exception {
        authed(get(PROBE + "/missing-parent"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ─── 500 ────────────────────────────────────────────────────────────────

    @Test
    void unexpectedErrorIs500WithErrorIdAndNoLeak(CapturedOutput output) throws Exception {
        String body = authed(get(PROBE + "/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String errorId = JsonPath.read(body, "$.errorId");
        assertThat(UUID.fromString(errorId)).isNotNull();
        assertThat(body).doesNotContain("password_hash", "SELECT", "IllegalStateException", "at com.localspot");
        assertThat(JsonPath.<String>read(body, "$.detail")).contains(errorId);
        // Log có cùng errorId kèm stack trace để tra đúng dòng
        assertThat(output.getAll()).contains("errorId=" + errorId).contains("IllegalStateException");
    }

    @Test
    void unclassifiedDatabaseErrorIs500() throws Exception {
        authed(get(PROBE + "/bad-sql"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.errorId").isString());
    }

    // ─── Lỗi ngoài Spring MVC (/error) ──────────────────────────────────────

    @Test
    void containerErrorPageUsesSameProblemFormat(CapturedOutput output) throws Exception {
        // Mô phỏng container chuyển tới /error khi một filter ném lỗi (vd. CSDL sập lúc nạp quyền)
        String body = mvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/me")
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("Mất kết nối CSDL")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String errorId = JsonPath.read(body, "$.errorId");
        assertThat(body).doesNotContain("Mất kết nối CSDL");
        assertThat(output.getAll()).contains("errorId=" + errorId);

        mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errorId").doesNotExist());
    }

    // ─── Hỗ trợ ──────────────────────────────────────────────────────────────

    private ResultActions authed(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(jwt()));
    }

    /** Endpoint chỉ có trong test để kích từng loại lỗi. */
    @RestController
    @RequestMapping(PROBE)
    static class Probe {

        private final JdbcTemplate jdbc;

        Probe(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @GetMapping("/typed")
        String typed(@RequestParam @Min(1) int n) {
            return "ok";
        }

        @GetMapping("/optimistic-lock")
        String optimisticLock() {
            throw new ObjectOptimisticLockingFailureException(Place.class, 1L);
        }

        @GetMapping("/duplicate-key")
        String duplicateKey() {
            jdbc.execute((Connection c) -> {
                try (var st = c.createStatement()) {
                    st.execute("CREATE TEMPORARY TABLE IF NOT EXISTS tmp_err_unique (v INT PRIMARY KEY)");
                    st.execute("INSERT IGNORE INTO tmp_err_unique (v) VALUES (1)");
                    st.execute("INSERT INTO tmp_err_unique (v) VALUES (1)");
                }
                return null;
            });
            return "unreachable";
        }

        @GetMapping("/check-violation")
        String checkViolation() {
            jdbc.execute((Connection c) -> {
                try (var st = c.createStatement()) {
                    st.execute("CREATE TEMPORARY TABLE IF NOT EXISTS tmp_err_check (v INT CHECK (v > 0))");
                    st.execute("INSERT INTO tmp_err_check (v) VALUES (-1)");
                }
                return null;
            });
            return "unreachable";
        }

        @GetMapping("/missing-parent")
        String missingParent() {
            // user_id không tồn tại → FK chặn (1452), không có dòng nào được ghi
            jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT 0, MIN(id) FROM roles");
            return "unreachable";
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("SELECT password_hash FROM users — chi tiết nội bộ không được lộ");
        }

        @GetMapping("/bad-sql")
        String badSql() {
            jdbc.queryForObject("SELEC 1", Integer.class);
            return "unreachable";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig {
        @Bean
        Probe errorProbe(JdbcTemplate jdbc) {
            return new Probe(jdbc);
        }
    }
}
