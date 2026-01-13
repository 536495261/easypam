package com.neu.easypam.common.ratelimit;

import java.lang.annotation.*;

/**
 * 滑动窗口限流注解
 * 基于 Redis ZSET 实现
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    
    /**
     * 限流key前缀
     */
    String key() default "";
    
    /**
     * 时间窗口（秒）
     */
    int window() default 1;
    
    /**
     * 窗口内最大请求数
     */
    int maxRequests() default 1;
    
    /**
     * 限流维度
     */
    LimitType limitType() default LimitType.IP;
    
    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";
}
