package com.localspot.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.localspot.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Danh mục permission có ba nơi khai báo — bảng {@code permissions} (V2), {@link Permissions} và {@code x-permission}
 * của openapi — test này giữ chúng khớp nhau. Đồng thời quét mọi endpoint đã đăng ký:
 *
 * <ul>
 *   <li>endpoint thuộc nhóm vai trò ({@code /admin}, {@code /moderation}, {@code /owner}) bắt buộc có
 *       {@code @PreAuthorize} — luật URL của SecurityConfig chỉ đòi đăng nhập, quên chú thích là mở cho mọi thành viên;
 *   <li>mọi authority nhắc trong {@code @PreAuthorize} phải là permission có thật — gõ sai thì không ai qua được mà
 *       không có lỗi nào báo.
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermissionCatalogTests {

    private static final Pattern PERMISSION = Pattern.compile("[a-z]+(?:-[a-z]+)*:[a-z]+(?:-[a-z]+)*");
    private static final Pattern ROLE_SCOPED_PATH = Pattern.compile("^/api/v1/(admin|moderation|owner)(/.*)?$");
    /** Maven chạy test với thư mục làm việc là {@code backend/}; CI checkout cả repo nên đường dẫn này tồn tại. */
    private static final Path OPENAPI = Path.of("..", "docs", "api", "openapi.yaml");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void databaseSeedMatchesPermissionConstants() {
        Set<String> seeded = new HashSet<>(jdbc.queryForList("SELECT name FROM permissions", String.class));
        assertThat(seeded).containsExactlyInAnyOrderElementsOf(Permissions.ALL);
    }

    @Test
    void openApiPermissionsMatchPermissionConstants() throws IOException {
        Set<String> documented = new HashSet<>();
        for (String line : Files.readAllLines(OPENAPI)) {
            int idx = line.indexOf("x-permission:");
            if (idx >= 0) {
                documented.addAll(permissionsIn(line.substring(idx + "x-permission:".length())));
            }
        }
        assertThat(documented).containsExactlyInAnyOrderElementsOf(Permissions.ALL);
    }

    @Test
    void roleScopedEndpointsRequirePreAuthorize() {
        List<String> unguarded = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            if (pathsOf(info).stream().anyMatch(p -> ROLE_SCOPED_PATH.matcher(p).matches())
                    && preAuthorizeOf(method) == null) {
                unguarded.add(info + " → " + method);
            }
        });
        assertThat(unguarded).as("endpoint nhóm vai trò thiếu @PreAuthorize").isEmpty();
    }

    @Test
    void preAuthorizeReferencesOnlyKnownPermissions() {
        List<String> unknown = new ArrayList<>();
        int guarded = 0;
        for (HandlerMethod method : handlerMapping.getHandlerMethods().values()) {
            PreAuthorize annotation = preAuthorizeOf(method);
            if (annotation == null) {
                continue;
            }
            guarded++;
            for (String permission : permissionsIn(annotation.value())) {
                if (!Permissions.ALL.contains(permission)) {
                    unknown.add(method + " → " + permission);
                }
            }
        }
        assertThat(guarded).as("phải có ít nhất các endpoint /admin/users").isGreaterThanOrEqualTo(4);
        assertThat(unknown).isEmpty();
    }

    private static PreAuthorize preAuthorizeOf(HandlerMethod method) {
        PreAuthorize onMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), PreAuthorize.class);
        return onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), PreAuthorize.class);
    }

    private static Set<String> pathsOf(RequestMappingInfo info) {
        return info.getPathPatternsCondition() != null
                ? info.getPathPatternsCondition().getPatternValues()
                : Set.of();
    }

    private static Set<String> permissionsIn(String text) {
        Set<String> found = new HashSet<>();
        Matcher matcher = PERMISSION.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}
