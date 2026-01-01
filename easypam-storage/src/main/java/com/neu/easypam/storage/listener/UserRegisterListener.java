package com.neu.easypam.storage.listener;

import com.neu.easypam.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 监听用户注册消息，初始化存储空间
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "user-register-topic",
        consumerGroup = "storage-consumer-group"
)
public class UserRegisterListener implements RocketMQListener<Long> {

    private final StorageService storageService;

    @Override
    public void onMessage(Long userId) {
        log.info("收到用户注册消息，userId: {}", userId);
        try {
            storageService.initStorage(userId);
        } catch (Exception e) {
            log.error("初始化用户{}存储空间失败", userId, e);
        }
    }
}
