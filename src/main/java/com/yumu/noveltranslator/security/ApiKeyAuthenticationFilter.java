package com.yumu.noveltranslator.security;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.entity.ApiKey;
import com.yumu.noveltranslator.mapper.ApiKeyMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * API Key 认证过滤器
 * 支持 Authorization: Bearer nt_sk_xxxx 格式的 API Key 认证
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String API_KEY_PREFIX = "nt_sk_";

    @Autowired
    private ApiKeyMapper apiKeyMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 如果已有认证，跳过
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        String jwt = parseJwt(request);
        if (jwt == null || !jwt.startsWith(API_KEY_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        // 查找 API Key
        ApiKey apiKey = apiKeyMapper.findByApiKey(jwt);
        if (apiKey == null || !apiKey.getActive()) {
            logger.warn("API Key 无效或已禁用: {}", maskApiKey(jwt));
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "API Key 无效或已禁用");
            return;
        }

        // 更新最后使用时间
        apiKey.setLastUsedAt(LocalDateTime.now());
        apiKeyMapper.updateById(apiKey);

        // 加载用户并设置 CustomUserDetails 认证信息
        var apiUser = userMapper.selectById(apiKey.getUserId());
        if (apiUser == null) {
            logger.warn("API Key 关联的用户不存在: userId={}", apiKey.getUserId());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "用户不存在");
            return;
        }

        // Set tenant context for API Key auth (fallback to 0L to match DB DEFAULT 0)
        TenantContext.setTenantId(apiUser.getTenantId() != null ? apiUser.getTenantId() : 0L);

        // 设置认证信息（使用 CustomUserDetails 以兼容 SecurityUtil）
        CustomUserDetails userDetails = new CustomUserDetails(apiUser);
        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // 在 request attribute 中标记这是 API Key 认证
        request.setAttribute("apiKeyId", apiKey.getId());
        request.setAttribute("authenticatedUserId", apiUser.getId());

        chain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() < 16) return "***";
        return key.substring(0, 10) + "..." + key.substring(key.length() - 4);
    }
}
