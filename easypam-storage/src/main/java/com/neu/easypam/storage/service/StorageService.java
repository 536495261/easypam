package com.neu.easypam.storage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neu.easypam.storage.entity.UserStorage;
import com.neu.easypam.storage.vo.StorageStatsVO;

public interface StorageService extends IService<UserStorage> {
    /**
     * 获取用户存储信息（懒加载：不存在则自动创建）
     */
    UserStorage getOrCreateByUserId(Long userId);
    
    /**
     * 获取用户存储信息（不自动创建）
     */
    UserStorage getByUserId(Long userId);
    
    /**
     * 获取存储空间统计信息
     */
    StorageStatsVO getStorageStats(Long userId);
    
    void initStorage(Long userId);
    
    /**
     * 检查空间是否足够（返回布尔值）
     */
    boolean checkSpace(Long userId, Long fileSize);
    
    /**
     * 校验空间是否足够（空间不足时抛出异常）
     */
    void validateSpace(Long userId, Long fileSize);
    
    void addUsedSpace(Long userId, Long fileSize);
    void reduceUsedSpace(Long userId, Long fileSize);

}
