package com.neu.easypam.common.ratelimit;

/**
 * 限流维度枚举
 */
public enum LimitType {
    /**
     * 按IP限流
     */
    IP,
    
    /**
     * 按用户ID限流
     */
    USER,
    
    /**
     * 全局限流
     */
    GLOBAL
}
