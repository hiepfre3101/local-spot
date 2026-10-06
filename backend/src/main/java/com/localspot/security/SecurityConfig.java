package com.localspot.security;

import com.localspot.config.SecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật (phần lõi — CLAUDE.md §3).
 *
 * <ul>
 *   <li><b>Stateless</b>: không session; xác thực bằng {@code Authorization: Bearer <access token>} qua
 *       BearerTokenAuthenticationFilter của Spring Security (OAuth2 Resource Server), không tự viết filter.
 *   <li><b>CSRF tắt</b>: access token nằm trong header (trình duyệt không tự gửi) nên không bị CSRF. Cookie duy nhất là
 *       refresh token — {@code SameSite=Strict} + {@code Path=/api/v1/auth} (S1), trình duyệt không gửi nó trong request
 *       xuất phát từ site khác.
 *   <li><b>Mặc định cần đăng nhập</b>: endpoint công khai phải khai báo rõ ở đây (danh sách trắng) — quên khai báo thì
 *       lỗi theo hướng an toàn (401), không lộ dữ liệu.
 *   <li><b>Phân quyền ở tầng phương thức</b>: luật URL ở đây chỉ phân biệt công khai / cần đăng nhập; quyền cụ thể khai
 *       báo bằng {@code @PreAuthorize} theo permission ({@link Permissions}) ngay trên endpoint, cạnh nơi đọc được
 *       {@code x-permission} của openapi. {@code AccessDeniedException} được {@code GlobalExceptionHandler} chuyển thành
 *       403 (hoặc 401 nếu chưa đăng nhập). Quyền trên bản ghi cụ thể ({@code *-own}) kiểm tra bằng
 *       {@code hasPermission(id, loại, permission)} qua {@link OwnershipPermissionEvaluator}.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final int MIN_HS256_KEY_BYTES = 32;

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http, UserJwtAuthenticationConverter jwtConverter, ProblemDetailsSecurityHandler problems)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Gửi lại mail xác thực cần biết là ai → phải đăng nhập (khai báo trước luật permitAll bên
                        // dưới)
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/resend-verification")
                        .authenticated()
                        // Đăng ký, đăng nhập, refresh, đăng xuất, xác thực email, quên / đặt lại mật khẩu
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**")
                        .permitAll()
                        // Kiểm tra nghi trùng khi đề xuất (U7) cần đăng nhập — khai báo trước luật công khai
                        // /places/{slug} bên dưới, vì "duplicates" cũng khớp mẫu một đoạn {slug}
                        .requestMatchers(HttpMethod.GET, "/api/v1/places/duplicates")
                        .authenticated()
                        // Danh sách + chi tiết địa điểm (FR-10, FR-13) công khai; chi tiết địa điểm chưa duyệt do
                        // service tự giới hạn người xem
                        .requestMatchers(HttpMethod.GET, "/api/v1/places", "/api/v1/places/*")
                        .permitAll()
                        // Danh mục, tiện ích (openapi Catalog, security: []) — form và bộ lọc của khách
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/amenities")
                        .permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems));
        return http.build();
    }

    /**
     * Gắn {@link OwnershipPermissionEvaluator} cho {@code hasPermission(...)} trong {@code @PreAuthorize}. Bean
     * {@code static} + {@code @Lazy}: hạ tầng method security được tạo rất sớm; tiêm thẳng evaluator (phụ thuộc
     * repository) sẽ kéo JPA khởi tạo sớm theo — proxy lazy chỉ tìm evaluator ở lần kiểm tra quyền đầu tiên.
     */
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            @Lazy PermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }

    @Bean
    SecretKey jwtSigningKey(SecurityProperties properties) {
        byte[] bytes = Base64.getDecoder().decode(properties.jwt().secret());
        if (bytes.length < MIN_HS256_KEY_BYTES) {
            // Khóa ngắn hơn đầu ra của HMAC-SHA256 làm yếu chữ ký — từ chối khởi động thay vì chạy với khóa yếu
            throw new IllegalStateException("localspot.security.jwt.secret phải là base64 của ít nhất "
                    + MIN_HS256_KEY_BYTES + " byte (hiện " + bytes.length + ")");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    /** Chỉ chấp nhận HS256 (chặn tấn công đổi thuật toán) + kiểm tra issuer và hạn dùng (lệch đồng hồ 60 s). */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }

    /**
     * BCrypt cost 10 (mặc định) — khớp hash trong seed demo. Không dùng DelegatingPasswordEncoder ({@code {bcrypt}}
     * prefix) vì hệ thống chỉ có một thuật toán; đổi thuật toán sau này cần migration hash riêng.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
