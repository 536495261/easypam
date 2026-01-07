package com.neu.easypam.notify.mq;

import com.neu.easypam.common.mq.OperationLogMessage;
import com.neu.easypam.notify.entity.OperationLog;
import com.neu.easypam.notify.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 操作日志消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "operation-log-topic",
        consumerGroup = "easypam-operation-log-consumer"
)
public class OperationLogConsumer implements RocketMQListener<OperationLogMessage> {

    private final OperationLogService operationLogService;

    @Override
    public void onMessage(OperationLogMessage message) {
        try {
            OperationLog logEntity = new OperationLog();
            logEntity.setUserId(message.getUserId());
            logEntity.setOperation(message.getOperation());
            logEntity.setTargetType(message.getTargetType());
            logEntity.setTargetId(message.getTargetId());
            logEntity.setTargetName(message.getTargetName());
            logEntity.setIp(message.getIp());
            logEntity.setUserAgent(message.getUserAgent());
            logEntity.setCreateTime(message.getCreateTime());

            operationLogService.save(logEntity);
            log.debug("操作日志保存成功：userId={}, operation={}", message.getUserId(), message.getOperation());
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
        }
    }
}
