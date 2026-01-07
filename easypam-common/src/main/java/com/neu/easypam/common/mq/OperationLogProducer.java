package com.neu.easypam.common.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 操作日志消息生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnProperty(name = "rocketmq.producer.group")
public class OperationLogProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public void send(OperationLogMessage message) {
        try {
            rocketMQTemplate.convertAndSend(OperationLogMessage.TOPIC, message);
            log.debug("发送操作日志：userId={}, operation={}, target={}", 
                    message.getUserId(), message.getOperation(), message.getTargetName());
        } catch (Exception e) {
            log.error("发送操作日志失败", e);
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
