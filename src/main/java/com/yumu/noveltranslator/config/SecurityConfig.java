package com.yumu.noveltranslator.config;

import com.yumu.noveltranslator.security.ApiKeyAuthenticationFilter;
import com.yumu.noveltranslator.security.JwtAuthenticationEntryPoint;
import com.yumu.noveltranslator.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF (CORS 由 NGINX 处理)
            .csrf(csrf -> csrf.disable())

            // 设置无状态，不创建 Session
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 设置权限异常处理器
            .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))

            // 配置 URL 访问权限（白名单路径见 SecurityPermitAllPaths）
            .authorizeHttpRequests(authz -> authz
                // 允许所有 OPTIONS 请求
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 从共享配置中加载白名单路径
                .requestMatchers(SecurityPermitAllPaths.PERMIT_ALL_PATHS.toArray(new String[0])).permitAll()
                // 支持 /** 通配符覆盖的子路径
                .requestMatchers(
                    "/static/**", "/css/**", "/js/**", "/images/**",
                    "/swagger-ui/**", "/v3/api-docs/**",
                    "/actuator/**",
                    "/v1/translate/text",
                    "/v1/translate/selection",
                    "/v1/translate/reader",
                    "/v1/translate/webpage",
                    "/v1/translate/premium_selection",
                    "/v1/translate/premium_reader",
                    "/v1/translate/document",
                    "/v1/translate/document/stream",
                    "/v1/translate/document/stream/**",
                    "/v1/translate/task/**"
                ).permitAll()

                // 用户个人资料接口需要认证
                .requestMatchers("/user/profile").authenticated()

                // 其他请求都需要身份验证
                .anyRequest().authenticated()
            );

        // 添加 API Key 过滤器（在 JWT 之前执行）
        http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // 添加 JWT 过滤器
        http.addFilterBefore(authenticationTokenFilter(), ApiKeyAuthenticationFilter.class);

        return http.build();
    }
}