package com.neu.easypam.storage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neu.easypam.common.exception.BusinessException;
import com.neu.easypam.common.mq.NotifyProducer;
import com.neu.easypam.storage.entity.UserStorage;
import com.neu.easypam.storage.mapper.UserStorageMapper;
import com.neu.easypam.storage.service.StorageService;
import com.neu.easypam.storage.vo.StorageStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl extends ServiceImpl<UserStorageMapper, UserStorage> implements StorageService {

    private final NotifyProducer notifyProducer;

    // 默认存储空间：10GB
    private static final Long DEFAULT_TOTAL_SPACE = 10L * 1024 * 1024 * 1024;
    // 预警阈值
    private static final int WARNING_THRESHOLD = 80;  // 80%
    private static final int CRITICAL_THRESHOLD = 95; // 95%

    @Override
    public UserStorage getOrCreateByUserId(Long userId) {
        UserStorage storage = getByUserId(userId);
        if (storage == null) {
            log.info("用户{}存储空间不存在，执行懒加载初始化", userId);
            initStorage(userId);
            storage = getByUserId(userId);
        }
        return storage;
    }

    @Override
    public UserStorage getByUserId(Long userId) {
        return getOne(new LambdaQueryWrapper<UserStorage>()
                .eq(UserStorage::getUserId, userId));
    }
    @Override
    public StorageStatsVO getStorageStats(Long userId) {
        UserStorage storage = getOrCreateByUserId(userId);
        long freeSpace = storage.getTotalSpace() - storage.getUsedSpace();
        double usagePercent = storage.getTotalSpace() > 0 
                ? BigDecimal.valueOf(storage.getUsedSpace() * 100.0 / storage.getTotalSpace())
                    .setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        
        return StorageStatsVO.builder()
                .totalSpace(storage.getTotalSpace())
                .usedSpace(storage.getUsedSpace())
                .freeSpace(freeSpace)
                .usagePercent(usagePercent)
                .totalSpaceText(formatSize(storage.getTotalSpace()))
                .usedSpaceText(formatSize(storage.getUsedSpace()))
                .freeSpaceText(formatSize(freeSpace))
                .level(storage.getLevel())
                .build();
    }
    @Override
    public void initStorage(Long userId) {
        // 检查是否已存在
        UserStorage existing = getByUserId(userId);
        if (existing != null) {
            log.warn("用户{}的存储空间已存在，跳过初始化", userId);
            return;
        }
        
        UserStorage storage = new UserStorage();
        storage.setUserId(userId);
        storage.setTotalSpace(DEFAULT_TOTAL_SPACE);
        storage.setUsedSpace(0L);
        storage.setLevel(1);
        save(storage);
        log.info("用户{}存储空间初始化成功，总空间：{}GB", userId, DEFAULT_TOTAL_SPACE / 1024 / 1024 / 1024);
    }

    @Override
    public boolean existsByUserId(Long userId) {
        return count(new LambdaQueryWrapper<UserStorage>()
                .eq(UserStorage::getUserId, userId)) > 0;
    }

    @Override
    public boolean checkSpace(Long userId, Long fileSize) {
        UserStorage storage = getOrCreateByUserId(userId);
        return storage.getUsedSpace() + fileSize <= storage.getTotalSpace();
    }

    @Override
    public void validateSpace(Long userId, Long fileSize) {
        UserStorage storage = getOrCreateByUserId(userId);
        long freeSpace = storage.getTotalSpace() - storage.getUsedSpace();
        if (fileSize > freeSpace) {
            throw new BusinessException("存储空间不足，剩余空间：" + formatSize(freeSpace) + "，文件大小：" + formatSize(fileSize));
        }
    }

    @Override
    public void addUsedSpace(Long userId, Long fileSize) {
        UserStorage storage = getOrCreateByUserId(userId);
        storage.setUsedSpace(storage.getUsedSpace() + fileSize);
        updateById(storage);
        
        // 检查是否需要发送预警通知
        checkAndSendWarning(userId, storage);
    }

    /**
     * 检查并发送存储空间预警
     */
    private void checkAndSendWarning(Long userId, UserStorage storage) {
        int usedPercent = (int) (storage.getUsedSpace() * 100 / storage.getTotalSpace());
        
        if (usedPercent >= CRITICAL_THRESHOLD) {
            notifyProducer.sendStorageFull(userId);
            log.warn("用户{}存储空间已满({}%)", userId, usedPercent);
        } else if (usedPercent >= WARNING_THRESHOLD) {
            notifyProducer.sendStorageWarning(userId, usedPercent);
            log.info("用户{}存储空间预警({}%)", userId, usedPercent);
        }
    }

    @Override
    public void reduceUsedSpace(Long userId, Long fileSize) {
        UserStorage storage = getOrCreateByUserId(userId);
        long newUsedSpace = storage.getUsedSpace() - fileSize;
        storage.setUsedSpace(Math.max(0, newUsedSpace));
        updateById(storage);
    }

    /**
     * 格式化文件大小
     */
    private String formatSize(Long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return BigDecimal.valueOf(bytes / 1024.0).setScale(2, RoundingMode.HALF_UP) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return BigDecimal.valueOf(bytes / (1024.0 * 1024)).setScale(2, RoundingMode.HALF_UP) + " MB";
        } else {
            return BigDecimal.valueOf(bytes / (1024.0 * 1024 * 1024)).setScale(2, RoundingMode.HALF_UP) + " GB";
        }
    }
}
