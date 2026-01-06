package com.neu.easypam.search.service;

import java.util.List;

/**
 * 搜索历史服务
 */
public interface SearchHistoryService {

    /**
     * 保存搜索历史
     */
    void save(Long userId, String keyword);

    /**
     * 获取搜索历史（最近N条）
     */
    List<String> list(Long userId, int limit);

    /**
     * 删除单条搜索历史
     */
    void delete(Long userId, String keyword);

    /**
     * 清空搜索历史
     */
    void clear(Long userId);
}
