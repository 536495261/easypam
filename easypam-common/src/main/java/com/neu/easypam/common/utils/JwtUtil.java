package com.neu.easypam.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * JWT工具类
 */
public class JwtUtil {
    public static final String SECRET = "easypam-secret-key-must-be-at-least-256-bits-long";
    public static final long ACCESS_TOKEN_EXPIRE = 30 * 60 * 1000L; // 30分钟
    public static final long REFRESH_TOKEN_EXPIRE = 7 * 24 * 60 * 60 * 1000L; // 7天
    public static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    public  static final String TOKEN_BLACKLIST_PREFIX = "token_blacklist:";
    private static SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成短期token
     * @param claims
     * @return
     */
    public static String generateAccessToken(Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRE))
                .signWith(getKey())
                .compact();
    }

    /**
     * 生成长期token
     * @param userId
     * @param deviceId
     * @return
     */
    public static String generateRefreshToken(Long userId,String deviceId) {
        return Jwts.builder()
                .claims(Map.of("userId", userId, "deviceId", deviceId,"type", "refresh"))
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRE))
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析过期的token
     * @param token
     * @return
     */
    public static Claims parseExpiredToken(String token) {
        try{
            return parseToken(token);
        }catch(ExpiredJwtException e){
            return e.getClaims();
        }
    }

    /**
     * 解析token的id，用于黑名单
     * @param token
     * @return
     */
    public static String getTokenId(String token) {
        Claims claims = parseToken(token);
        return claims.getId();
    }

    /**
     * 获取token的剩余时间
     * @param token
     * @return
     */
    public static long getTokenRemainTime(String token) {
        Claims claims = parseToken(token);
        Date expiration = claims.getExpiration();
        return expiration.getTime() - System.currentTimeMillis();
    }
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取设备标识
     * @param token
     * @return
     */
    public static String getDeviceId(String token) {
        Claims claims = parseToken(token);
        return claims.get("deviceId", String.class);
    }

    public boolean isRefreshToken(String token) {
        Claims claims = parseToken(token);
        return "refresh".equals(claims.get("type", String.class));
    }
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }
}
