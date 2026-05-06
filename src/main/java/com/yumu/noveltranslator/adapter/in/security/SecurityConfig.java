package com.yumu.noveltranslator.adapter.in.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.config.tenant.TenantCleanupInterceptor;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.ApiKeyMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserMapper;
import com.yumu.noveltranslator.adapter.out.redis.ApiKeyCacheService;
import com.yumu.noveltranslator.adapter.out.redis.TokenBlacklistService;
import com.yumu.noveltranslator.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final TenantCleanupInterceptor tenantCleanupInterceptor;
    private final ObjectMapper objectMapper;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final TokenBlacklistService tokenBlacklistService;
    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyCacheService apiKeyCacheService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${translation.ip-rate-limit.window-seconds:60}")
    private long ipWindowSeconds;

    @Value("${translation.ip-rate-limit.max-requests:100}")
    private int ipMaxRequests;

    @Value("${translation.key-rate-limit.window-seconds:60}")
    private long keyWindowSeconds;

    @Value("${translation.key-rate-limit.max-requests:1000}")
    private int keyMaxRequests;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Create filter instances directly (no longer @Component)
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtUtils, userMapper, tokenBlacklistService);
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter =
                new ApiKeyAuthenticationFilter(apiKeyCacheService, apiKeyMapper, userMapper);
        TranslationRateLimitFilter translationRateLimitFilter =
                new TranslationRateLimitFilter(ipRateLimiter(), keyRateLimiter(), objectMapper);
        SecurityHeadersFilter securityHeadersFilter = new SecurityHeadersFilter();

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .authorizeHttpRequests(authz -> authz
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(SecurityPermitAllPaths.PERMIT_ALL_PATHS.toArray(new String[0])).permitAll()
                .requestMatchers("/admin/**").authenticated()
                .requestMatchers("/v1/translate/selection", "/v1/translate/reader", "/v1/translate/webpage", "/v1/translate/text/stream").authenticated()
                .requestMatchers("/v1/translate/**").authenticated()
                .anyRequest().authenticated()
            );

        // All custom filters positioned before UsernamePasswordAuthenticationFilter.
        // Order: translationRateLimit → apiKeyAuth → jwtAuth
        http.addFilterBefore(translationRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(tenantCleanupInterceptor, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public RedisSlidingWindowRateLimiter ipRateLimiter() {
        return new RedisSlidingWindowRateLimiter(
                stringRedisTemplate,
                "translation:ip_limit:",
                ipWindowSeconds,
                ipMaxRequests);
    }

    @Bean
    public RedisSlidingWindowRateLimiter keyRateLimiter() {
        return new RedisSlidingWindowRateLimiter(
                stringRedisTemplate,
                "translation:key_limit:",
                keyWindowSeconds,
                keyMaxRequests);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        String allowedOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        List<String> origins;
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            origins = List.of(allowedOrigins.split(","));
        } else {
            origins = List.of(
                "http://localhost:7341",
                "chrome-extension://imhobepmncjlobbicamollfjldiodi"
            );
        }
        final List<String> allowedOriginsList = origins;

        return (HttpServletRequest request) -> {
            String origin = request.getHeader("Origin");
            if (origin == null || !allowedOriginsList.contains(origin)) {
                return null;
            }
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOriginPatterns(List.of(origin));
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("*"));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);
            return config;
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
