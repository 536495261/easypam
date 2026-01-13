package com.neu.easypam.notify.mq;

import com.neu.easypam.common.mq.NotifyMessage;
import com.neu.easypam.notify.entity.Notification;
import com.neu.easypam.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 通知消息消费者（支持幂等和重试）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "notify-topic",
        consumerGroup = "easypam-notify-consumer",
        consumeMode = ConsumeMode.CONCURRENTLY,
        maxReconsumeTimes = 3,
        consumeThreadNumber = 20  // 并发消费，可以设置更高
)
public class NotifyConsumer implements RocketMQListener<NotifyMessage> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(NotifyMessage message) {
        String messageId = message.getMessageId();
        log.info("收到通知消息：messageId={}, type={}, userId={}", 
                messageId, message.getType(), message.getUserId());

        try {
            // 幂等性检查：通过 messageId 去重
            if (messageId != null && notificationService.existsByMessageId(messageId)) {
                log.info("通知消息已处理，跳过：messageId={}", messageId);
                return;
            }

            // 保存到数据库
            Notification notification = new Notification();
            notification.setMessageId(messageId);
            notification.setUserId(message.getUserId());
            notification.setType(message.getType());
            notification.setTitle(message.getTitle());
            notification.setContent(message.getContent());
            notification.setBizId(message.getBizId());
            notification.setFromUserId(message.getFromUserId());
            notification.setIsRead(0);
            notification.setCreateTime(message.getCreateTime());

            notificationService.save(notification);
            log.info("通知保存成功：id={}, messageId={}", notification.getId(), messageId);
        } catch (DuplicateKeyException e) {
            log.info("通知消息重复，跳过：messageId={}", messageId);
        } catch (Exception e) {
            log.error("处理通知消息失败，将进行重试：messageId={}", messageId, e);
            throw new RuntimeException("通知处理失败: " + e.getMessage(), e);
        }
    }
}
