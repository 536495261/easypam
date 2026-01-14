package com.neu.easypam.file.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.service.FileCacheService;
import com.neu.easypam.file.service.FileService;
import com.neu.easypam.file.vo.CacheStatsVO;
import com.neu.easypam.file.vo.HotFileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class FileCacheServiceImpl implements FileCacheService {

    private final Cache<Long, FileInfo> fileMetadataCache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final FileService fileService;

    private static final String REDIS_FILE_PREFIX = "file:info:";
    private static final String HOT_FILE_KEY = "file:hot:access";
    private static final Duration REDIS_EXPIRE = Duration.ofMinutes(30);

    public FileCacheServiceImpl(Cache<Long, FileInfo> fileMetadataCache,
                                 RedisTemplate<String, Object> redisTemplate,
                                 @Lazy FileService fileService) {
        this.fileMetadataCache = fileMetadataCache;
        this.redisTemplate = redisTemplate;
        this.fileService = fileService;
    }

    @Override
    public FileInfo getFileInfo(Long fileId) {
        // 1. L1 本地缓存
        FileInfo fileInfo = fileMetadataCache.getIfPresent(fileId);
        if (fileInfo != null) {
            log.debug("L1 缓存命中：fileId={}", fileId);
            return fileInfo;
        }

        // 2. L2 Redis 缓存
        String redisKey = REDIS_FILE_PREFIX + fileId;
        fileInfo = (FileInfo) redisTemplate.opsForValue().get(redisKey);
        if (fileInfo != null) {
            // 回填 L1
            fileMetadataCache.put(fileId, fileInfo);
            log.debug("L2 缓存命中：fileId={}", fileId);
            return fileInfo;
        }

        // 3. 查数据库
        fileInfo = fileService.getById(fileId);
        if (fileInfo != null) {
            cacheFileInfo(fileInfo);
            log.debug("DB 查询：fileId={}", fileId);
        }

        return fileInfo;
    }

    @Override
    public void cacheFileInfo(FileInfo fileInfo) {
        if (fileInfo == null || fileInfo.getId() == null) {
            return;
        }
        // L1 本地缓存
        fileMetadataCache.put(fileInfo.getId(), fileInfo);
        // L2 Redis 缓存
        String redisKey = REDIS_FILE_PREFIX + fileInfo.getId();
        redisTemplate.opsForValue().set(redisKey, fileInfo, REDIS_EXPIRE);
    }

    @Override
    public void evictFileInfo(Long fileId) {
        // 清除 L1
        fileMetadataCache.invalidate(fileId);
        // 清除 L2
        redisTemplate.delete(REDIS_FILE_PREFIX + fileId);
        log.debug("缓存清除：fileId={}", fileId);
    }

    @Override
    public void recordAccess(Long fileId) {
        // 使用 Redis ZSET 记录访问频次，score 为访问次数
        redisTemplate.opsForZSet().incrementScore(HOT_FILE_KEY, fileId.toString(), 1);
    }

    /**
     * 清理冷数据：只保留 Top 1000，防止 ZSET 无限增长
     * 由定时任务调用
     */
    public void cleanupColdData() {
        Long size = redisTemplate.opsForZSet().zCard(HOT_FILE_KEY);
        if (size != null && size > 1000) {
            // 删除排名 1000 之后的数据
            redisTemplate.opsForZSet().removeRange(HOT_FILE_KEY, 0, size - 1001);
            log.info("清理热点冷数据，删除 {} 条", size - 1000);
        }
    }

    /**
     * 热度衰减：所有分数减半，让近期访问权重更高
     * 由定时任务调用（每天执行）
     */
    public void decayHotScore() {
        Set<ZSetOperations.TypedTuple<Object>> all = redisTemplate.opsForZSet()
                .rangeWithScores(HOT_FILE_KEY, 0, -1);
        if (all == null || all.isEmpty()) {
            return;
        }
        for (ZSetOperations.TypedTuple<Object> tuple : all) {
            Double score = tuple.getScore();
            if (score != null && score > 1) {
                // 分数减半
                redisTemplate.opsForZSet().add(HOT_FILE_KEY, tuple.getValue(), score / 2);
            }
        }
        log.info("热度衰减完成，处理 {} 条", all.size());
    }

    @Override
    public List<HotFileVO> getHotFiles(int limit) {
        Set<ZSetOperations.TypedTuple<Object>> topFiles = redisTemplate.opsForZSet()
                .reverseRangeWithScores(HOT_FILE_KEY, 0, limit - 1);

        List<HotFileVO> result = new ArrayList<>();
        if (topFiles == null || topFiles.isEmpty()) {
            return result;
        }

        for (ZSetOperations.TypedTuple<Object> tuple : topFiles) {
            Long fileId = Long.parseLong(tuple.getValue().toString());
            Double score = tuple.getScore();

            FileInfo fileInfo = getFileInfo(fileId);
            if (fileInfo != null && fileInfo.getDeleted() == 0) {
                HotFileVO vo = new HotFileVO();
                vo.setFileId(fileId);
                vo.setFileName(fileInfo.getFileName());
                vo.setFileType(fileInfo.getFileType());
                vo.setFileSize(fileInfo.getFileSize());
                vo.setAccessCount(score != null ? score.longValue() : 0);
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    public CacheStatsVO getCacheStats() {
        CacheStatsVO stats = new CacheStatsVO();

        // 本地缓存统计
        CacheStats caffeineStats = fileMetadataCache.stats();
        stats.setLocalCacheSize(fileMetadataCache.estimatedSize());
        stats.setLocalHitCount(caffeineStats.hitCount());
        stats.setLocalMissCount(caffeineStats.missCount());
        stats.setLocalHitRate(caffeineStats.hitRate());

        // Redis 统计（简化）
        Set<String> keys = redisTemplate.keys(REDIS_FILE_PREFIX + "*");
        stats.setRedisCacheSize(keys != null ? (long) keys.size() : 0L);

        // 热点文件数量
        Long hotCount = redisTemplate.opsForZSet().zCard(HOT_FILE_KEY);
        stats.setHotFileCount(hotCount != null ? hotCount.intValue() : 0);

        return stats;
    }
}
