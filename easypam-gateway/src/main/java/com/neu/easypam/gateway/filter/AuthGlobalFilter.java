package com.neu.easypam.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String SECRET = "easypam-secret-key-must-be-at-least-256-bits-long";
    private static final String TOKEN_BLACKLIST_PREFIX = "token_blacklist:";  // 与JwtUtil保持一致
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    
    private final ReactiveStringRedisTemplate redisTemplate;
    
    // 白名单路径
    private static final List<String> WHITE_LIST = List.of(
            "/user/login",
            "/user/register",
            "/user/captcha",
            "/user/captcha/refresh",
            "/user/refresh",
            "/share/public/**",
            // Swagger文档相关
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/swagger-ui/**",
            "/favicon.ico",
            // 各服务的API文档
            "/user/v3/api-docs/**",
            "/file/v3/api-docs/**",
            "/storage/v3/api-docs/**",
            "/share/v3/api-docs/**",
            "/search/v3/api-docs/**",
            "/notify/v3/api-docs/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        
        // 白名单放行
        if (isWhitePath(path)) {
            return chain.filter(exchange);
        }

        // 获取token
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,"TOKEN_MISSING", "未提供有效的认证信息");
        }
        String finalToken = token.substring(7);
        String jti;
        try{
            Claims claims = parseToken(finalToken);
            jti = claims.getId();
        }catch (ExpiredJwtException e){
            // Token过期，返回特定错误码，前端可用refreshToken刷新
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "登录已过期，请刷新Token");
        }catch (Exception e){
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "无效的认证信息");
        }
        // 检查Token是否在黑名单中（使用jti而非整个token）
        String blacklistKey = TOKEN_BLACKLIST_PREFIX + (jti != null ? jti : finalToken);
        // 检查Token是否在黑名单中
        return redisTemplate.hasKey(blacklistKey)
                .flatMap(inBlacklist -> {
                    if (Boolean.TRUE.equals(inBlacklist)) {
                        log.warn("Token已被加入黑名单");
                        return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED", "登录已失效，请重新登录");
                    }
                    return validateAndForward(exchange, chain, finalToken);
                });
    }

    private Mono<Void> validateAndForward(ServerWebExchange exchange, GatewayFilterChain chain, String token) {
        try {
            Claims claims = parseToken(token);
            Long userId = claims.get("userId", Long.class);
            String username = claims.get("username", String.class);
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-User-Name", username != null ? username : "")
                    .build();
            return chain.filter(exchange.mutate().request(request).build());
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "登录已过期，请刷新Token");
        } catch (Exception e) {
            log.error("Token验证失败: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "无效的认证信息");
        }
    }
    /**
     * 返回统一错误响应
     */
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"code\":\"%s\",\"message\":\"%s\",\"data\":null}", code, message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private boolean isWhitePath(String path) {
        return WHITE_LIST.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
    
    @Override
    public int getOrder() {
        return -100;
    }
}
