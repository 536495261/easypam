package com.neu.easypam.common.ratelimit;

import com.neu.easypam.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * 滑动窗口限流切面
 * 使用 Redis ZSET 实现精确的滑动窗口算法
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Lua脚本：滑动窗口限流
     * KEYS[1]: 限流key
     * ARGV[1]: 当前时间戳（毫秒）
     * ARGV[2]: 窗口大小（毫秒）
     * ARGV[3]: 最大请求数
     * ARGV[4]: 唯一请求ID
     */
    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local maxRequests = tonumber(ARGV[3])
            local requestId = ARGV[4]
            
            -- 移除窗口外的请求
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            
            -- 获取当前窗口内的请求数
            local currentCount = redis.call('ZCARD', key)
            
            if currentCount < maxRequests then
                -- 添加当前请求
                redis.call('ZADD', key, now, requestId)
                -- 设置过期时间
                redis.call('PEXPIRE', key, window)
                return 1
            else
                return 0
            end
            """;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        String key = buildKey(point, rateLimit);
        
        long now = System.currentTimeMillis();
        long windowMs = rateLimit.window() * 1000L;
        String requestId = now + "-" + Thread.currentThread().getId();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);
        List<String> keys = Collections.singletonList(key);
        
        Long result = stringRedisTemplate.execute(script, keys,
                String.valueOf(now),
                String.valueOf(windowMs),
                String.valueOf(rateLimit.maxRequests()),
                requestId);

        if (result == null || result == 0) {
            log.warn("触发限流: key={}, window={}s, maxRequests={}", key, rateLimit.window(), rateLimit.maxRequests());
            throw new BusinessException(rateLimit.message());
        }

        return point.proceed();
    }

    /**
     * 构建限流key
     */
    private String buildKey(ProceedingJoinPoint point, RateLimit rateLimit) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        
        StringBuilder keyBuilder = new StringBuilder("rate_limit:");
        
        // 添加自定义前缀或方法名
        if (!rateLimit.key().isEmpty()) {
            keyBuilder.append(rateLimit.key());
        } else {
            keyBuilder.append(method.getDeclaringClass().getSimpleName())
                    .append(":")
                    .append(method.getName());
        }
        
        // 根据限流类型添加后缀
        switch (rateLimit.limitType()) {
            case IP -> keyBuilder.append(":").append(getClientIp());
            case USER -> keyBuilder.append(":").append(getUserId());
            case GLOBAL -> {} // 全局限流不需要后缀
        }
        
        return keyBuilder.toString();
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 获取用户ID
     */
    private String getUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "anonymous";
        }
        HttpServletRequest request = attributes.getRequest();
        String userId = request.getHeader("X-User-Id");
        return userId != null ? userId : "anonymous";
    }
}
