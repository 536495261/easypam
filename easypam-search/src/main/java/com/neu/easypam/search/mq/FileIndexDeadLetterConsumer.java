package com.neu.easypam.search.mq;

import com.neu.easypam.common.mq.FileIndexMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 文件索引消息死信队列消费者
 * 处理消费失败超过最大重试次数的消息
 * 
 * 死信队列 Topic 命名规则：%DLQ% + 原消费者组名
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "%DLQ%search-index-consumer",
        consumerGroup = "search-index-dlq-consumer"
)
public class FileIndexDeadLetterConsumer implements RocketMQListener<FileIndexMessage> {

    @Override
    public void onMessage(FileIndexMessage message) {
        log.error("【死信队列】文件索引消息消费最终失败：type={}, fileId={}, fileName={}, userId={}",
                message.getType(),
                message.getFileId(),
                message.getFileName(),
                message.getUserId());
        
        // 处理方案（根据业务需求选择）：
        // 1. 记录到数据库，便于人工排查和重新索引
        // 2. 发送告警通知
        // 3. 对于索引失败，可以后续通过定时任务全量同步修复
        
        // TODO: 可以保存到失败消息表
        // deadMessageService.save(message);
    }
}
