package com.neu.easypam.search.service.impl;

import com.neu.easypam.search.service.SearchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 搜索历史服务实现（基于 Redis ZSet）
 */
@Service
@RequiredArgsConstructor
public class SearchHistoryServiceImpl implements SearchHistoryService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "search:history:";
    private static final int MAX_HISTORY_SIZE = 20;  // 最多保存20条

    @Override
    public void save(Long userId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return;
        }
        String key = KEY_PREFIX + userId;
        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
        
        // 用时间戳作为 score，实现按时间排序
        zSet.add(key, keyword.trim(), System.currentTimeMillis());
        
        // 保留最近 N 条，删除旧的
        Long size = zSet.size(key);
        if (size != null && size > MAX_HISTORY_SIZE) {
            zSet.removeRange(key, 0, size - MAX_HISTORY_SIZE - 1);
        }
    }

    @Override
    public List<String> list(Long userId, int limit) {
        String key = KEY_PREFIX + userId;
        // 按 score 倒序获取（最新的在前）
        Set<String> result = redisTemplate.opsForZSet()
                .reverseRange(key, 0, limit - 1);
        
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }
        return result.stream().collect(Collectors.toList());
    }

    @Override
    public void delete(Long userId, String keyword) {
        String key = KEY_PREFIX + userId;
        redisTemplate.opsForZSet().remove(key, keyword);
    }

    @Override
    public void clear(Long userId) {
        String key = KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }
}
