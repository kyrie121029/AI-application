package com.example.demo.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类 —— 生成 Token、校验 Token、从 Token 中提取用户信息
 * <p>
 * JWT 结构：Header.Payload.Signature（三段 Base64 以 . 分隔）
 * <p>
 * 流程：
 *   登录成功 → 服务端生成 Token 返回给客户端
 *   后续请求 → 客户端在 Header 里带 Authorization: Bearer <token>
 *   服务端校验 → 从 Token 解析出 userId，不需要查 Session
 */
@Component
public class JwtUtil {

    // 签名密钥（至少256位 = 32字符），生产环境应放环境变量
    @Value("${jwt.secret:this-is-a-very-long-secret-key-for-jwt-signing-2024!!}")
    private String secret;

    // Token 有效期（毫秒），默认 24 小时
    @Value("${jwt.expiration:86400000}")
    private long expiration;

    /**
     * 生成 Token
     *
     * @param userId   用户 ID，存在 Payload 里
     * @param username 用户名
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();

        return Jwts.builder()
                .subject(String.valueOf(userId))         // 主题 = 用户 ID
                .claim("username", username)             // 自定义字段
                .issuedAt(now)                            // 签发时间
                .expiration(new Date(now.getTime() + expiration)) // 过期时间
                .signWith(key)                            // 签名
                .compact();
    }

    /**
     * 从 Token 中提取用户 ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 从 Token 中提取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 校验 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析 Token（验证签名 + 过期时间）
     */
    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}