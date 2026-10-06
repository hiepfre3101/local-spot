package com.localspot.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.security.AuthenticatedUser;
import com.localspot.security.UserAuthentication;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ActivityLogAspect} trên MySQL thật: log ghi <b>trong cùng transaction</b> với thao tác (thứ tự advice do
 * {@code TransactionConfig} đặt) — log lỗi thì thao tác rollback, thao tác lỗi thì không có log. Bean {@link Audited} chỉ
 * có trong test, ghi một thẻ (bảng {@code tags}) làm "thao tác" để kiểm rollback.
 */
@Import({TestcontainersConfiguration.class, ActivityLogAspectTests.Config.class})
@SpringBootTest
@ActiveProfiles("test")
class ActivityLogAspectTests {

    @Autowired
    private Audited audited;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void writesLogWithActorTargetAndMetadata() {
        long actorId = signIn();
        String slug = slug();

        audited.createTag(slug, "Ghi chú");

        Map<String, Object> log = jdbc.queryForMap(
                "SELECT actor_id, target_type, metadata FROM activity_log"
                        + " WHERE action = 'TAG_CREATE' AND target_id = ?",
                tagId(slug));
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(actorId);
        assertThat(log.get("target_type")).isEqualTo("TAG");
        assertThat(JsonPath.<String>read((String) log.get("metadata"), "$.note"))
                .isEqualTo("Ghi chú");
        assertThat(JsonPath.<List<String>>read((String) log.get("metadata"), "$.slugs"))
                .containsExactly(slug);
    }

    @Test
    void failedLogRollsBackTheAction() {
        signIn();
        String slug = slug();

        // targetId trả null → aspect ném lỗi sau khi thao tác đã chạy → cả transaction rollback
        assertThatThrownBy(() -> audited.createTagWithBrokenAudit(slug)).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tags WHERE slug = ?", Integer.class, slug))
                .isZero();
    }

    @Test
    void failedActionWritesNoLog() {
        signIn();
        String slug = slug();
        Integer before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_log WHERE action = 'TAG_CREATE_FAIL'", Integer.class);

        assertThatThrownBy(() -> audited.createTagThenFail(slug)).isInstanceOf(IllegalArgumentException.class);

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM activity_log WHERE action = 'TAG_CREATE_FAIL'", Integer.class))
                .isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tags WHERE slug = ?", Integer.class, slug))
                .isZero();
    }

    @Test
    void requiresAuthenticatedActor() {
        String slug = slug();

        assertThatThrownBy(() -> audited.createTag(slug, "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đăng nhập");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tags WHERE slug = ?", Integer.class, slug))
                .isZero();
    }

    /** Người dùng thật (FK {@code activity_log.actor_id}) đặt vào SecurityContext như filter JWT làm. */
    private long signIn() {
        String email = "audit-" + UUID.randomUUID() + "@localspot.test";
        jdbc.update(
                "INSERT INTO users (email, password_hash, display_name, created_at, updated_at)"
                        + " VALUES (?, 'x', 'Kiểm thử', NOW(6), NOW(6))",
                email);
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        SecurityContextHolder.getContext()
                .setAuthentication(new UserAuthentication(new AuthenticatedUser(id, email), null, List.of()));
        return id;
    }

    private static String slug() {
        return "audit-" + UUID.randomUUID();
    }

    private long tagId(String slug) {
        return jdbc.queryForObject("SELECT id FROM tags WHERE slug = ?", Long.class, slug);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        Audited audited(JdbcTemplate jdbc) {
            return new Audited(jdbc);
        }
    }

    static class Audited {

        private final JdbcTemplate jdbc;

        Audited(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional
        @AuditedAction(
                action = "TAG_CREATE",
                targetType = "TAG",
                targetId = "#result",
                metadata = "{note: #note, slugs: {#slug}}")
        public Long createTag(String slug, String note) {
            return insert(slug);
        }

        @Transactional
        @AuditedAction(action = "TAG_CREATE", targetType = "TAG", targetId = "null")
        public Long createTagWithBrokenAudit(String slug) {
            return insert(slug);
        }

        @Transactional
        @AuditedAction(action = "TAG_CREATE_FAIL", targetType = "TAG", targetId = "#result")
        public Long createTagThenFail(String slug) {
            insert(slug);
            throw new IllegalArgumentException("thao tác lỗi sau khi đã ghi");
        }

        private Long insert(String slug) {
            jdbc.update("INSERT INTO tags (name, slug) VALUES (?, ?)", slug.substring(0, 20), slug);
            return jdbc.queryForObject("SELECT id FROM tags WHERE slug = ?", Long.class, slug);
        }
    }
}
