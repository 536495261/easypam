package com.neu.easypam.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neu.easypam.file.entity.LocalMessage;
import com.neu.easypam.file.mapper.LocalMessageMapper;
import com.neu.easypam.file.service.LocalMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class LocalMessageServiceImpl extends ServiceImpl<LocalMessageMapper, LocalMessage>
        implements LocalMessageService {

    @Override
    public void saveMessage(String topic, String messageBody, String hashKey) {
        LocalMessage message = new LocalMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setTopic(topic);
        message.setMessageBody(messageBody);
        message.setHashKey(hashKey);
        message.setStatus(LocalMessage.Status.PENDING);
        message.setRetryCount(0);
        message.setMaxRetry(5);
        message.setNextRetryTime(LocalDateTime.now());
        save(message);
        log.debug("保存本地消息：messageId={}, topic={}", message.getMessageId(), topic);
    }

    @Override
    public void markSuccess(String messageId) {
        update(new LambdaUpdateWrapper<LocalMessage>()
                .eq(LocalMessage::getMessageId, messageId)
                .set(LocalMessage::getStatus, LocalMessage.Status.SUCCESS));
        log.debug("消息发送成功：messageId={}", messageId);
    }

    @Override
    public void markRetry(String messageId, String errorMsg) {
        LocalMessage message = getOne(new LambdaQueryWrapper<LocalMessage>()
                .eq(LocalMessage::getMessageId, messageId));
        
        if (message == null) {
            return;
        }

        int newRetryCount = message.getRetryCount() + 1;
        
        if (newRetryCount >= message.getMaxRetry()) {
            // 超过最大重试次数，标记为失败
            update(new LambdaUpdateWrapper<LocalMessage>()
                    .eq(LocalMessage::getMessageId, messageId)
                    .set(LocalMessage::getStatus, LocalMessage.Status.FAILED)
                    .set(LocalMessage::getRetryCount, newRetryCount)
                    .set(LocalMessage::getErrorMsg, errorMsg));
            log.error("消息发送失败，超过最大重试次数：messageId={}", messageId);
        } else {
            // 设置下次重试时间（指数退避：10s, 30s, 1min, 2min, 5min）
            // 状态从 PROCESSING 回到 PENDING
            int[] delays = {10, 30, 60, 120, 300};
            int delaySeconds = delays[Math.min(newRetryCount - 1, delays.length - 1)];
            
            update(new LambdaUpdateWrapper<LocalMessage>()
                    .eq(LocalMessage::getMessageId, messageId)
                    .set(LocalMessage::getStatus, LocalMessage.Status.PENDING)
                    .set(LocalMessage::getRetryCount, newRetryCount)
                    .set(LocalMessage::getNextRetryTime, LocalDateTime.now().plusSeconds(delaySeconds))
                    .set(LocalMessage::getErrorMsg, errorMsg));
            log.info("消息将在{}秒后重试：messageId={}, retryCount={}", delaySeconds, messageId, newRetryCount);
        }
    }

    @Override
    public List<LocalMessage> getPendingMessages(int limit) {
        return list(new LambdaQueryWrapper<LocalMessage>()
                .eq(LocalMessage::getStatus, LocalMessage.Status.PENDING)
                .le(LocalMessage::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(LocalMessage::getNextRetryTime)
                .last("LIMIT " + limit));
    }

    @Override
    public boolean tryLock(String messageId) {
        // 使用乐观锁：只有状态为 PENDING 时才能更新为 PROCESSING
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<LocalMessage>()
                .eq(LocalMessage::getMessageId, messageId)
                .eq(LocalMessage::getStatus, LocalMessage.Status.PENDING)
                .set(LocalMessage::getStatus, LocalMessage.Status.PROCESSING));
        return rows > 0;
    }
}
