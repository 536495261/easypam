package com.neu.easypam.share.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.neu.easypam.common.mq.LocalMessageHandler;
import com.neu.easypam.share.entity.LocalMessage;
import com.neu.easypam.share.mapper.LocalMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Share服务的本地消息处理实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareLocalMessageHandler implements LocalMessageHandler {

    private final LocalMessageMapper localMessageMapper;

    @Override
    public void saveFailedMessage(String topic, String messageBody, String hashKey) {
        LocalMessage message = new LocalMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setTopic(topic);
        message.setMessageBody(messageBody);
        message.setHashKey(hashKey);
        message.setStatus(LocalMessage.Status.PENDING);
        message.setRetryCount(0);
        message.setMaxRetry(5);
        message.setNextRetryTime(LocalDateTime.now());
        localMessageMapper.insert(message);
        log.debug("保存本地消息：messageId={}, topic={}", message.getMessageId(), topic);
    }

    @Override
    public void markSuccess(String messageId) {
        localMessageMapper.update(null, new LambdaUpdateWrapper<LocalMessage>()
                .eq(LocalMessage::getMessageId, messageId)
                .set(LocalMessage::getStatus, LocalMessage.Status.SUCCESS));
    }

    @Override
    public void markRetry(String messageId, String errorMsg) {
        LocalMessage message = localMessageMapper.selectOne(
                new LambdaQueryWrapper<LocalMessage>().eq(LocalMessage::getMessageId, messageId));
        
        if (message == null) return;

        int newRetryCount = message.getRetryCount() + 1;
        
        if (newRetryCount >= message.getMaxRetry()) {
            localMessageMapper.update(null, new LambdaUpdateWrapper<LocalMessage>()
                    .eq(LocalMessage::getMessageId, messageId)
                    .set(LocalMessage::getStatus, LocalMessage.Status.FAILED)
                    .set(LocalMessage::getRetryCount, newRetryCount)
                    .set(LocalMessage::getErrorMsg, errorMsg));
            log.error("消息发送失败，超过最大重试次数：messageId={}", messageId);
        } else {
            // 状态从 PROCESSING 回到 PENDING
            int[] delays = {10, 30, 60, 120, 300};
            int delaySeconds = delays[Math.min(newRetryCount - 1, delays.length - 1)];
            
            localMessageMapper.update(null, new LambdaUpdateWrapper<LocalMessage>()
                    .eq(LocalMessage::getMessageId, messageId)
                    .set(LocalMessage::getStatus, LocalMessage.Status.PENDING)
                    .set(LocalMessage::getRetryCount, newRetryCount)
                    .set(LocalMessage::getNextRetryTime, LocalDateTime.now().plusSeconds(delaySeconds))
                    .set(LocalMessage::getErrorMsg, errorMsg));
        }
    }

    /**
     * 获取待重试的消息
     */
    public List<LocalMessage> getPendingMessages(int limit) {
        return localMessageMapper.selectList(new LambdaQueryWrapper<LocalMessage>()
                .eq(LocalMessage::getStatus, LocalMessage.Status.PENDING)
                .le(LocalMessage::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(LocalMessage::getNextRetryTime)
                .last("LIMIT " + limit));
    }

    /**
     * 尝试锁定消息（乐观锁，防止并发处理）
     */
    public boolean tryLock(String messageId) {
        int rows = localMessageMapper.update(null, new LambdaUpdateWrapper<LocalMessage>()
                .eq(LocalMessage::getMessageId, messageId)
                .eq(LocalMessage::getStatus, LocalMessage.Status.PENDING)
                .set(LocalMessage::getStatus, LocalMessage.Status.PROCESSING));
        return rows > 0;
    }
}
