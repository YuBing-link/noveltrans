package com.yumu.noveltranslator.security;

import com.yumu.noveltranslator.config.tenant.TenantCleanupInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final TenantCleanupInterceptor tenantCleanupInterceptor;

    @Bean
    public JwtAuthenticationFilter authenticationTokenFilter() {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // CORS 白名单：仅允许配置的可信域名访问，禁止 "*" 与 allowCredentials 同时使用
        String allowedOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        List<String> origins;
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            origins = List.of(allowedOrigins.split(","));
        } else {
            // 开发环境默认允许本地前端端口
            origins = List.of("http://localhost:5173", "http://localhost:3000");
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // CSRF 已禁用：本项目使用 JWT + Authorization header 认证，
            // 不依赖浏览器自动携带的 Cookie 认证，因此不受 CSRF 攻击影响。
            // 如果未来引入 Cookie 认证，需要启用 CSRF 保护。
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(SecurityPermitAllPaths.PERMIT_ALL_PATHS.toArray(new String[0])).permitAll()
                // 翻译端点必须认证：防止匿名请求消耗配额和费用
                .requestMatchers("/v1/translate/selection", "/v1/translate/reader", "/v1/translate/webpage", "/v1/translate/text/stream").authenticated()
                .requestMatchers("/v1/translate/**").authenticated()
                .anyRequest().authenticated()
            );

        http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(authenticationTokenFilter(), ApiKeyAuthenticationFilter.class);
        http.addFilterAfter(tenantCleanupInterceptor, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
