package com.neu.easypam.file.service;

import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.vo.CacheStatsVO;
import com.neu.easypam.file.vo.HotFileVO;

import java.util.List;

/**
 * 文件缓存服务
 * 多级缓存 + 热点识别
 */
public interface FileCacheService {

    /**
     * 获取文件元数据（优先缓存）
     */
    FileInfo getFileInfo(Long fileId);

    /**
     * 缓存文件元数据
     */
    void cacheFileInfo(FileInfo fileInfo);

    /**
     * 删除文件缓存
     */
    void evictFileInfo(Long fileId);

    /**
     * 记录文件访问（用于热点识别）
     */
    void recordAccess(Long fileId);

    /**
     * 获取热点文件列表
     */
    List<HotFileVO> getHotFiles(int limit);

    /**
     * 获取缓存统计
     */
    CacheStatsVO getCacheStats();
}
