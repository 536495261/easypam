package com.neu.easypam.notify.mq;

import com.neu.easypam.common.mq.NotifyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 通知消息死信队列消费者
 * 处理消费失败超过最大重试次数的消息
 * 
 * 死信队列 Topic 命名规则：%DLQ% + 原消费者组名
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "%DLQ%easypam-notify-consumer",
        consumerGroup = "easypam-notify-dlq-consumer"
)
public class NotifyDeadLetterConsumer implements RocketMQListener<NotifyMessage> {

    @Override
    public void onMessage(NotifyMessage message) {
        log.error("【死信队列】通知消息消费最终失败：messageId={}, userId={}, type={}, title={}",
                message.getMessageId(),
                message.getUserId(),
                message.getType(),
                message.getTitle());
        
        // 处理方案（根据业务需求选择）：
        // 1. 记录到数据库，便于人工排查
        // 2. 发送告警通知（邮件/钉钉/企微）
        // 3. 尝试降级处理
        
        // TODO: 可以保存到失败消息表，供后续人工处理
        // deadMessageService.save(message);
    }
}
