package com.yumu.noveltranslator.security;

import com.yumu.noveltranslator.config.SecurityPermitAllPaths;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.util.JwtUtils;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserMapper userMapper;

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

            // 从 token 中提取用户信息
            String email = decodedJWT.getClaim("email").asString();
            Long userId = decodedJWT.getClaim("userId").asLong();

            if (email == null || userId == null) {
                logger.warn("JWT Token 缺少用户信息");
                chain.doFilter(request, response);
                return;
            }

            // 从数据库加载用户，构建 CustomUserDetails
            User user = userMapper.findByEmail(email);
            if (user == null || !user.getId().equals(userId)) {
                logger.warn("JWT Token 对应的用户不存在: email={}, userId={}", email, userId);
                chain.doFilter(request, response);
                return;
            }

            CustomUserDetails userDetails = new CustomUserDetails(user);
            UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (TokenExpiredException e) {
            logger.warn("JWT Token 已过期: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"401\",\"message\":\"Token 已过期，请重新登录\",\"error\":\"token_expired\"}");
            return;
        } catch (JWTVerificationException e) {
            logger.warn("JWT Token 验证失败: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"401\",\"message\":\"Token 验证失败，无效凭证\",\"error\":\"invalid_token\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 判断请求路径是否在白名单中（委托给共享配置）
     */
    private boolean isExcludedPath(String requestURI) {
        return SecurityPermitAllPaths.isPermitted(requestURI);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}
