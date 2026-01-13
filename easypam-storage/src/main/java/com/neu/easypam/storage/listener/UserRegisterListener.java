package com.neu.easypam.storage.listener;

import com.neu.easypam.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 监听用户注册消息，初始化存储空间（支持幂等和重试）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "user-register-topic",
        consumerGroup = "storage-consumer-group",
        consumeMode = ConsumeMode.CONCURRENTLY,
        maxReconsumeTimes = 3  // 最多重试3次
)
public class UserRegisterListener implements RocketMQListener<Long> {

    private final StorageService storageService;

    @Override
    public void onMessage(Long userId) {
        log.info("收到用户注册消息，userId: {}", userId);
        try {
            // 幂等性检查：已存在则跳过
            if (storageService.existsByUserId(userId)) {
                log.info("用户{}存储空间已存在，跳过初始化", userId);
                return;
            }
            
            storageService.initStorage(userId);
            log.info("用户{}存储空间初始化成功", userId);
        } catch (DuplicateKeyException e) {
            // 并发插入导致的重复，正常情况
            log.info("用户{}存储空间已存在（并发插入），跳过", userId);
        } catch (Exception e) {
            log.error("初始化用户{}存储空间失败，将进行重试", userId, e);
            // 抛出异常触发重试
            throw new RuntimeException("存储空间初始化失败: " + e.getMessage(), e);
        }
    }
}
