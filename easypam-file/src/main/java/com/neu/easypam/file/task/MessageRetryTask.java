package com.neu.easypam.file.task;

import com.neu.easypam.file.entity.LocalMessage;
import com.neu.easypam.file.mq.FileIndexProducer;
import com.neu.easypam.file.service.LocalMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息补偿重试定时任务
 * 扫描本地消息表中待发送的消息，进行补偿重试
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRetryTask {

    private final LocalMessageService localMessageService;
    private final FileIndexProducer fileIndexProducer;

    /**
     * 每30秒扫描一次待重试的消息
     */
    @Scheduled(fixedRate = 30000)
    public void retryPendingMessages() {
        List<LocalMessage> pendingMessages = localMessageService.getPendingMessages(100);
        
        if (pendingMessages.isEmpty()) {
            return;
        }

        log.info("开始补偿重试，待处理消息数：{}", pendingMessages.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (LocalMessage message : pendingMessages) {
            // 乐观锁：尝试锁定消息，防止多实例并发处理
            if (!localMessageService.tryLock(message.getMessageId())) {
                log.debug("消息已被其他实例处理：messageId={}", message.getMessageId());
                continue;
            }
            
            try {
                boolean success = fileIndexProducer.resend(
                        message.getMessageBody(),
                        message.getHashKey(),
                        message.getMessageId()
                );
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                failCount++;
                log.error("补偿重试异常：messageId={}", message.getMessageId(), e);
                localMessageService.markRetry(message.getMessageId(), e.getMessage());
            }
        }
        
        log.info("补偿重试完成，成功：{}，失败：{}", successCount, failCount);
    }
}
