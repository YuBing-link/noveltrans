package com.yumu.noveltranslator.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtils {

    private String secret;
    private Long expireTime;

    @Value("${jwt.secret}")
    public void setSecret(String secret) {
        this.secret = secret;
    }

    @Value("${jwt.expiration:2592000000}") // 默认 30 天 (单位毫秒)
    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }

    /**
     * 生成 Token
     * @param userId   用户 ID
     * @param email   用户邮箱
     * @param tenantId 租户 ID
     * @return String
     */
    public String createToken(Long userId, String email, Long tenantId) {
        Date date = new Date(System.currentTimeMillis() + expireTime);
        Algorithm algorithm = Algorithm.HMAC256(secret);

        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("email", email)
                .withClaim("tenantId", tenantId)
                .withIssuedAt(new Date())
                .withExpiresAt(date)
                .sign(algorithm);
    }

    /**
     * 生成 Token（向后兼容，使用 userId 作为 tenantId）
     */
    public String createToken(Long userId, String email) {
        return createToken(userId, email, userId);
    }

    /**
     * 校验并解码 Token
     * @param token 客户端传来的 Token
     * @return DecodedJWT 如果验证失败会抛出异常
     */
    public DecodedJWT verifyToken(String token) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        JWTVerifier verifier = JWT.require(algorithm).build();
        return verifier.verify(token);
    }

    /**
     * 获取 Token 中的用户信息
     */
    public Map<String, String> getUserInfoFromToken(String token) {
        DecodedJWT jwt = verifyToken(token);
        Map<String, String> info = new HashMap<>(2);
        info.put("userId", jwt.getClaim("userId").asLong().toString());
        info.put("email", jwt.getClaim("email").asString());
        return info;
    }

    /**
     * 从 Token 中获取用户 ID
     */
    public Long getUserIdFromToken(String token) {
        DecodedJWT jwt = verifyToken(token);
        return jwt.getClaim("userId").asLong();
    }

    /**
     * 从 Token 中获取用户邮箱
     */
    public String getEmailFromToken(String token) {
        DecodedJWT jwt = verifyToken(token);
        return jwt.getClaim("email").asString();
    }

    /**
     * 从 Token 中获取租户 ID
     */
    public Long getTenantIdFromToken(String token) {
        DecodedJWT jwt = verifyToken(token);
        return jwt.getClaim("tenantId").asLong();
    }
}
