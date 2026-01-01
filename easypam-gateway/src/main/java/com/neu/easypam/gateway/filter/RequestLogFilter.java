package com.neu.easypam.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 请求日志过滤器
 */
@Slf4j
@Component
public class RequestLogFilter implements GlobalFilter, Ordered {

    private static final String START_TIME = "startTime";
    private static final String SECRET = "easypam-secret-key-must-be-at-least-256-bits-long";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 记录开始时间
        exchange.getAttributes().put(START_TIME, System.currentTimeMillis());
        
        // 获取请求信息
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        String ip = getClientIp(request);
        String userId = getUserIdFromToken(request);
        
        log.info(">>> 请求开始 | {} {} | IP: {} | UserID: {}", method, path, ip, userId);
        
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // 计算耗时
            Long startTime = exchange.getAttribute(START_TIME);
            long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
            int statusCode = exchange.getResponse().getStatusCode() != null 
                    ? exchange.getResponse().getStatusCode().value() : 0;
            
            log.info("<<< 请求结束 | {} {} | 状态: {} | 耗时: {}ms", method, path, statusCode, duration);
        }));
    }

    /**
     * 从Token中解析UserId
     */
    private String getUserIdFromToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return "anonymous";
        }
        try {
            token = token.substring(7);
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Long userId = claims.get("userId", Long.class);
            return userId != null ? String.valueOf(userId) : "anonymous";
        } catch (Exception e) {
            return "invalid";
        }
    }

    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddress() != null 
                    ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
