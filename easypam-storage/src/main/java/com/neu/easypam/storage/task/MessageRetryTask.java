package com.neu.easypam.storage.task;

import com.neu.easypam.common.mq.NotifyMessage;
import com.neu.easypam.common.mq.NotifyProducer;
import com.neu.easypam.storage.entity.LocalMessage;
import com.neu.easypam.storage.mq.StorageLocalMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRetryTask {

    private final StorageLocalMessageHandler localMessageHandler;
    private final NotifyProducer notifyProducer;

    @Scheduled(fixedRate = 30000)
    public void retryPendingMessages() {
        List<LocalMessage> pendingMessages = localMessageHandler.getPendingMessages(100);
        
        if (pendingMessages.isEmpty()) {
            return;
        }

        log.info("开始补偿重试，待处理消息数：{}", pendingMessages.size());
        
        for (LocalMessage message : pendingMessages) {
            // 乐观锁：尝试锁定消息，防止多实例并发处理
            if (!localMessageHandler.tryLock(message.getMessageId())) {
                log.debug("消息已被其他实例处理：messageId={}", message.getMessageId());
                continue;
            }
            
            if (NotifyMessage.TOPIC.equals(message.getTopic())) {
                notifyProducer.resend(message.getMessageBody(), message.getMessageId());
            }
        }
    }
}
