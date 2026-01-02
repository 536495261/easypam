package com.neu.easypam.file.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neu.easypam.common.feign.StorageFeignClient;
import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 回收站自动清理任务
 * 每天凌晨2点执行，清理超过30天的文件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrashCleanupTask {

    private final FileService fileService;
    private final StorageFeignClient storageFeignClient;

    /**
     * 回收站保留天数
     */
    private static final int TRASH_RETENTION_DAYS = 30;

    /**
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredTrash() {
        log.info("开始执行回收站自动清理任务...");

        LocalDateTime expireTime = LocalDateTime.now().minusDays(TRASH_RETENTION_DAYS);

        // 查询所有过期的回收站文件
        List<FileInfo> expiredFiles = fileService.list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getDeleted, 1)
                .lt(FileInfo::getDeleteTime, expireTime));

        if (expiredFiles.isEmpty()) {
            log.info("没有需要清理的过期文件");
            return;
        }

        // 按用户分组统计释放空间
        Map<Long, Long> userSpaceMap = expiredFiles.stream()
                .filter(f -> f.getIsFolder() != 1)  // 只统计文件大小
                .collect(Collectors.groupingBy(
                        FileInfo::getUserId,
                        Collectors.summingLong(FileInfo::getFileSize)
                ));

        // 删除过期文件
        List<Long> expiredIds = expiredFiles.stream()
                .map(FileInfo::getId)
                .collect(Collectors.toList());
        fileService.removeBatchByIds(expiredIds);

        // 更新各用户的存储空间
        userSpaceMap.forEach((userId, freedSpace) -> {
            if (freedSpace > 0) {
                try {
                    storageFeignClient.reduceUsedSpace(userId, freedSpace);
                    log.info("用户{}释放空间：{}", userId, freedSpace);
                } catch (Exception e) {
                    log.error("更新用户{}存储空间失败", userId, e);
                }
            }
        });

        log.info("回收站清理完成，共删除{}个文件", expiredFiles.size());
    }
}
