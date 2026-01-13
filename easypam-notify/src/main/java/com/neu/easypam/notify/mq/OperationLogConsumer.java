package com.neu.easypam.notify.mq;

import com.neu.easypam.common.mq.OperationLogMessage;
import com.neu.easypam.notify.entity.OperationLog;
import com.neu.easypam.notify.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 操作日志消费者（支持幂等和重试）
 * 
 * 日志允许丢失，重试次数较少
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "operation-log-topic",
        consumerGroup = "easypam-operation-log-consumer",
        consumeMode = ConsumeMode.CONCURRENTLY,
        maxReconsumeTimes = 2,
        consumeThreadNumber = 20
)
public class OperationLogConsumer implements RocketMQListener<OperationLogMessage> {

    private final OperationLogService operationLogService;

    @Override
    public void onMessage(OperationLogMessage message) {
        String logId = message.getLogId();
        try {
            // 幂等性检查
            if (logId != null && operationLogService.existsByLogId(logId)) {
                log.debug("操作日志已存在，跳过：logId={}", logId);
                return;
            }

            OperationLog logEntity = new OperationLog();
            logEntity.setLogId(logId);
            logEntity.setUserId(message.getUserId());
            logEntity.setOperation(message.getOperation());
            logEntity.setTargetType(message.getTargetType());
            logEntity.setTargetId(message.getTargetId());
            logEntity.setTargetName(message.getTargetName());
            logEntity.setIp(message.getIp());
            logEntity.setUserAgent(message.getUserAgent());
            logEntity.setCreateTime(message.getCreateTime());

            operationLogService.save(logEntity);
            log.debug("操作日志保存成功：logId={}, userId={}, operation={}", 
                    logId, message.getUserId(), message.getOperation());
        } catch (DuplicateKeyException e) {
            log.debug("操作日志重复，跳过：logId={}", logId);
        } catch (Exception e) {
            log.error("保存操作日志失败，将进行重试：logId={}", logId, e);
            throw new RuntimeException("操作日志保存失败: " + e.getMessage(), e);
        }
    }
}
