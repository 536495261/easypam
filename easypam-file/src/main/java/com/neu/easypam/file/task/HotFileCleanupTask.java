package com.neu.easypam.file.task;

import com.neu.easypam.file.service.impl.FileCacheServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热点数据清理任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotFileCleanupTask {

    private final FileCacheServiceImpl fileCacheService;

    /**
     * 每天凌晨3点：热度衰减 + 清理冷数据
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyCleanup() {
        log.info("开始热点数据清理...");
        fileCacheService.decayHotScore();
        fileCacheService.cleanupColdData();
        log.info("热点数据清理完成");
    }
}
