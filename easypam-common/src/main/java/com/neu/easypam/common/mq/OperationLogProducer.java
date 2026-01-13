package com.neu.easypam.common.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 操作日志消息生产者（异步发送，不阻塞业务）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnProperty(name = "rocketmq.producer.group")
public class OperationLogProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 异步发送操作日志（不阻塞业务）
     */
    public void send(OperationLogMessage message) {
        try {
            rocketMQTemplate.asyncSend(OperationLogMessage.TOPIC, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("发送操作日志成功：logId={}, userId={}, operation={}", 
                            message.getLogId(), message.getUserId(), message.getOperation());
                }

                @Override
                public void onException(Throwable e) {
                    log.warn("发送操作日志失败：logId={}, error={}", message.getLogId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("发送操作日志异常：logId={}", message.getLogId(), e);
        }
    }

    public void log(Long userId, String operation, String targetType, Long targetId, String targetName) {
        OperationLogMessage msg = OperationLogMessage.create(userId, operation, targetType, targetId, targetName);
        send(msg);
    }

    public void log(Long userId, String operation, String targetType, Long targetId, 
                    String targetName, String ip, String userAgent) {
        OperationLogMessage msg = OperationLogMessage.create(userId, operation, targetType, targetId, targetName);
        msg.setIp(ip);
        msg.setUserAgent(userAgent);
        send(msg);
    }
}
