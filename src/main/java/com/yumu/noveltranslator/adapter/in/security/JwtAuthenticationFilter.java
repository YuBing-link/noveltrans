package com.yumu.noveltranslator.adapter.in.security;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.adapter.out.persistence.entity.User;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserMapper;
import com.yumu.noveltranslator.adapter.out.redis.JwtAuthCacheService;
import com.yumu.noveltranslator.adapter.out.redis.JwtAuthCacheService.JwtAuthInfo;
import com.yumu.noveltranslator.adapter.out.redis.TokenBlacklistService;
import com.yumu.noveltranslator.util.JwtUtils;
import com.yumu.noveltranslator.util.SecurityUtil;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器。
 *
 * 优化（ADR-008）：热路径零 MySQL
 * - Caffeine L1（5 分钟） → Redis L2（30 分钟） → MySQL 兜底
 * - 黑名单检查也优先 Redis，Redis 不可用时降级 MySQL
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtAuthCacheService jwtAuthCacheService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, UserMapper userMapper,
                                    TokenBlacklistService tokenBlacklistService,
                                    JwtAuthCacheService jwtAuthCacheService) {
        this.jwtUtils = jwtUtils;
        this.userMapper = userMapper;
        this.tokenBlacklistService = tokenBlacklistService;
        this.jwtAuthCacheService = jwtAuthCacheService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 跳过不需要认证的路径
        String requestURI = request.getRequestURI();
        if (isExcludedPath(requestURI)) {
            chain.doFilter(request, response);
            return;
        }

        // 如果已有认证（例如 API Key 过滤器已设置），跳过
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        // 从请求头中获取 JWT token
        String jwt = parseJwt(request);
        if (jwt == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // 验证 JWT token
            var decodedJWT = jwtUtils.verifyToken(jwt);

            String email = decodedJWT.getClaim("email").asString();
            Long userId = decodedJWT.getClaim("userId").asLong();

            if (email == null || userId == null) {
                sendUnauthorized(response, "Invalid token: missing user info");
                return;
            }

            // 1. 尝试从缓存获取（L1 Caffeine → L2 Redis）
            JwtAuthInfo cached = null;
            try {
                cached = jwtAuthCacheService.get(userId);
            } catch (Exception e) {
                log.warn("JWT 缓存读取失败，降级走 MySQL: {}", e.getMessage());
            }

            if (cached != null && !cached.isDisabled()) {
                // 缓存命中，零 MySQL 查询
                authenticateFromCache(request, response, chain, cached, jwt, email);
                return;
            }

            // 2. 缓存未命中 → 查 MySQL 黑名单 + 加载用户
            // 黑名单检查（MySQL，但频率极低，绝大多数用户不会被列入黑名单）
            if (tokenBlacklistService.isBlacklisted(jwt)) {
                sendUnauthorized(response, "Token has been revoked");
                return;
            }
            if (tokenBlacklistService.isEmailBlacklisted(email)) {
                jwtAuthCacheService.put(userId, JwtAuthInfo.disabled());
                sendUnauthorized(response, "Account access has been revoked");
                return;
            }

            // 加载用户
            User user = userMapper.findByEmail(email);
            if (user == null || !user.getId().equals(userId)) {
                sendUnauthorized(response, "Invalid token: user not found");
                return;
            }

            // 写入缓存（含 userId, email, userLevel, tenantId）
            JwtAuthInfo info = new JwtAuthInfo();
            info.setUserId(user.getId());
            info.setEmail(user.getEmail());
            info.setUserLevel(user.getUserLevel());
            info.setTenantId(user.getTenantId() != null ? user.getTenantId() : 0L);
            info.setDisabled(false);
            jwtAuthCacheService.put(userId, info);

            // 设置认证
            authenticateWithUser(request, response, chain, user);

        } catch (TokenExpiredException e) {
            log.debug("JWT Token 已过期: {}", e.getMessage());
            sendUnauthorized(response, "Token expired");
        } catch (JWTVerificationException e) {
            log.debug("JWT Token 验证失败: {}", e.getMessage());
            sendUnauthorized(response, "Invalid token");
        }

        // 认证成功，继续过滤器链
        chain.doFilter(request, response);
    }

    /**
     * 从缓存认证（跳过 MySQL 查询）
     */
    private void authenticateFromCache(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            JwtAuthInfo info, String jwt, String email) throws IOException, ServletException {

        // Set tenant context
        TenantContext.setTenantIdOrDefault(info.getTenantId());

        // 构造最小化 CustomUserDetails（从缓存信息构建，无需查 DB）
        CustomUserDetails userDetails = new CustomUserDetails(info.getUserId(), info.getUserLevel());
        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        request.setAttribute("authenticatedUserId", info.getUserId());
    }

    /**
     * 从 DB 加载的用户设置认证
     */
    private void authenticateWithUser(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            User user) throws IOException, ServletException {

        TenantContext.setTenantIdOrDefault(user.getTenantId());

        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        request.setAttribute("authenticatedUserId", user.getId());
    }

    private boolean isExcludedPath(String requestURI) {
        return SecurityPermitAllPaths.isPermitted(requestURI);
    }

    private String parseJwt(HttpServletRequest request) {
        String token = SecurityUtil.parseBearerToken(request);
        if (token != null && token.startsWith("nt_sk_")) {
            return null;
        }
        return token;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        FilterResponseUtil.writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "401", message);
    }
}
