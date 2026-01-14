package com.neu.easypam.file.vo;

import lombok.Data;

@Data
public class CacheStatsVO {
    // 本地缓存统计
    private Long localCacheSize;
    private Long localHitCount;
    private Long localMissCount;
    private Double localHitRate;

    // Redis 缓存统计
    private Long redisCacheSize;
    private Long redisHitCount;
    private Long redisMissCount;

    // 热点文件统计
    private Integer hotFileCount;
}
