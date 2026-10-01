package com.localspot.security;

import com.localspot.config.SecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
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
                        // Đăng ký, đăng nhập, refresh, đăng xuất, xác thực email, quên / đặt lại mật khẩu
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**")
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
