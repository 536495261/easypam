package com.neu.easypam.notify.mq;

import com.neu.easypam.common.mq.NotifyMessage;
import com.neu.easypam.notify.entity.Notification;
import com.neu.easypam.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 通知消息消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "notify-topic",
        consumerGroup = "easypam-notify-consumer"
)
public class NotifyConsumer implements RocketMQListener<NotifyMessage> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(NotifyMessage message) {
        log.info("收到通知消息：type={}, userId={}, title={}", 
                message.getType(), message.getUserId(), message.getTitle());

        try {
            // 保存到数据库
            Notification notification = new Notification();
            notification.setUserId(message.getUserId());
            notification.setType(message.getType());
            notification.setTitle(message.getTitle());
            notification.setContent(message.getContent());
            notification.setBizId(message.getBizId());
            notification.setFromUserId(message.getFromUserId());
            notification.setIsRead(0);
            notification.setCreateTime(message.getCreateTime());

            notificationService.save(notification);
            log.info("通知保存成功：id={}", notification.getId());
        } catch (Exception e) {
            log.error("处理通知消息失败", e);
        }
    }
}
