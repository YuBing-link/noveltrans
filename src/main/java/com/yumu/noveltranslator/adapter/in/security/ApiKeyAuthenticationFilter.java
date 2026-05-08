package com.yumu.noveltranslator.adapter.in.security;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.adapter.out.persistence.converter.UserConverter;
import com.yumu.noveltranslator.adapter.out.persistence.entity.ApiKey;
import com.yumu.noveltranslator.adapter.out.persistence.entity.User;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.ApiKeyMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserMapper;
import com.yumu.noveltranslator.adapter.out.redis.ApiKeyCacheService;
import com.yumu.noveltranslator.adapter.out.redis.ApiKeyCacheService.ApiKeyAuthInfo;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API Key 认证过滤器
 * 支持 Authorization: Bearer nt_sk_xxxx 格式的 API Key 认证
 *
 * 优化（ADR-008）：热路径零 MySQL
 * - Caffeine L1（5 分钟） → Redis L2（30 分钟） → MySQL 兜底
 * - Redis 不可达时 fail-closed（HTTP 503）
 * - incrementUsage 改为 Redis INCR，异步回写 MySQL
 */
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_PREFIX = "nt_sk_";

    private final ApiKeyCacheService apiKeyCacheService;
    private final ApiKeyMapper apiKeyMapper;
    private final UserMapper userMapper;

    public ApiKeyAuthenticationFilter(
            ApiKeyCacheService apiKeyCacheService,
            ApiKeyMapper apiKeyMapper,
            UserMapper userMapper) {
        this.apiKeyCacheService = apiKeyCacheService;
        this.apiKeyMapper = apiKeyMapper;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 如果已有认证，跳过
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        String token = SecurityUtil.parseBearerToken(request);
        if (token == null || !token.startsWith(API_KEY_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        // 1. 尝试从缓存获取（L1 Caffeine → L2 Redis）
        ApiKeyAuthInfo cached = null;
        try {
            cached = apiKeyCacheService.get(token);
        } catch (Exception e) {
            // Redis 读失败 → 降级走 MySQL 路径，不阻断认证
            log.warn("API Key 缓存读取失败，降级走 MySQL: {}", e.getMessage());
        }
        if (cached != null && !cached.isDisabled()) {
            // 缓存命中，零 MySQL 查询
            authenticateFromCache(request, response, chain, cached, token);
            return;
        }

        // 2. 缓存未命中 → 查 MySQL
        try {
            ApiKey apiKey = apiKeyMapper.findByApiKey(token);
            if (apiKey == null || !apiKey.getActive()) {
                // 写入负向缓存（disabled），避免后续请求反复查 DB
                apiKeyCacheService.put(token, ApiKeyAuthInfo.disabled());
                reject(response, HttpServletResponse.SC_UNAUTHORIZED, "API Key 无效或已禁用");
                return;
            }

            // 加载用户
            User user = userMapper.selectById(apiKey.getUserId());
            if (user == null) {
                reject(response, HttpServletResponse.SC_UNAUTHORIZED, "用户不存在");
                return;
            }

            // 写入缓存（含 userId, userLevel, tenantId）
            ApiKeyAuthInfo info = new ApiKeyAuthInfo();
            info.setApiKeyId(apiKey.getId());
            info.setUserId(user.getId());
            info.setUserLevel(user.getUserLevel());
            info.setTenantId(user.getTenantId() != null ? user.getTenantId() : 0L);
            info.setDisabled(false);
            apiKeyCacheService.put(token, info);

            // 记录使用次数（Redis INCR，异步回写 MySQL，失败不阻断）
            try {
                apiKeyCacheService.incrementUsage(apiKey.getId());
            } catch (Exception e) {
                log.warn("API Key usage 记录失败（非致命）: token={}", SecurityUtil.maskApiKey(token));
            }

            // 设置认证
            authenticateWithUser(request, response, chain, user, apiKey.getId());

        } catch (Exception e) {
            // DB 异常 → fail-closed（MySQL 不可达时拒绝所有请求，防止未认证流量涌入）
            log.error("API Key 认证失败（DB 异常），返回 503: {}", e.getMessage());
            FilterResponseUtil.writeJsonError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "503", "认证服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 从缓存认证（跳过 MySQL 查询）
     */
    private void authenticateFromCache(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            ApiKeyAuthInfo info, String token) throws IOException, ServletException {

        // Redis INCR 记录使用次数（失败不阻断认证）
        try {
            apiKeyCacheService.incrementUsage(info.getApiKeyId());
        } catch (Exception e) {
            log.warn("API Key usage 记录失败（非致命）: token={}", SecurityUtil.maskApiKey(token));
        }

        // 设置 tenant context
        TenantContext.setTenantId(info.getTenantId());

        // 构造最小化 CustomUserDetails（从缓存信息构建，无需查 DB）
        CustomUserDetails userDetails = new CustomUserDetails(info.getUserId(), info.getUserLevel(), info.getTenantId());
        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);

        request.setAttribute("apiKeyId", info.getApiKeyId());
        request.setAttribute("authenticatedUserId", info.getUserId());

        chain.doFilter(request, response);
    }

    /**
     * 从 DB 加载的用户设置认证
     */
    private void authenticateWithUser(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            User user, Long apiKeyId) throws IOException, ServletException {

        TenantContext.setTenantIdOrDefault(user.getTenantId());

        // Convert entity to domain model before creating CustomUserDetails
        com.yumu.noveltranslator.domain.model.User domainUser = UserConverter.toUserModel(user);
        CustomUserDetails userDetails = new CustomUserDetails(domainUser);
        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);

        request.setAttribute("apiKeyId", apiKeyId);
        request.setAttribute("authenticatedUserId", user.getId());

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        log.warn("API Key 认证拒绝: {}", message);
        FilterResponseUtil.writeJsonError(response, status, String.valueOf(status), message);
    }
}
