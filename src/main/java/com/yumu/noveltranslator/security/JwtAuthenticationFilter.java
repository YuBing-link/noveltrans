package com.yumu.noveltranslator.security;

import com.yumu.noveltranslator.config.SecurityPermitAllPaths;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 跳过不需要认证的路径
        String requestURI = request.getRequestURI();
        if (isExcludedPath(requestURI)) {
            chain.doFilter(request, response);
            return;
        }

        // 从请求头中获取 JWT token
        String jwt = parseJwt(request);
        if (jwt != null) {
            try {
                // 验证 JWT token
                var decodedJWT = jwtUtils.verifyToken(jwt);

                // 从 token 中提取用户名
                String email = decodedJWT.getClaim("email").asString();
                Long userId = decodedJWT.getClaim("userId").asLong();

                // 如果 SecurityContext 中没有认证信息，则进行认证
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                    // 这里我们使用一个简单的用户详情，实际应用中可能需要查询数据库
                    UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                        email,
                        "", // 密码为空，因为我们使用JWT
                        java.util.Collections.emptyList()); // 权限列表

                    // 创建认证对象
                    UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 在 SecurityContext 中设置认证信息
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (TokenExpiredException e) {
                logger.error("JWT Token 已过期: {}", e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token 已过期，请重新登录");
                return;
            } catch (JWTVerificationException e) {
                logger.error("JWT Token 验证失败: {}", e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token 验证失败，无效凭证");
                return;
            }
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