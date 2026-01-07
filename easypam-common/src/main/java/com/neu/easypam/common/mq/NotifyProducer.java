package com.neu.easypam.common.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 通知消息生产者（放在 common 模块，供各服务调用）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnProperty(name = "rocketmq.producer.group")
public class NotifyProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送通知消息
     */
    public void send(NotifyMessage message) {
        try {
            rocketMQTemplate.convertAndSend(NotifyMessage.TOPIC, message);
            log.info("发送通知消息：type={}, userId={}, title={}", 
                    message.getType(), message.getUserId(), message.getTitle());
        } catch (Exception e) {
            log.error("发送通知消息失败", e);
        }
    }

    /**
     * 发送分享通知
     */
    public void sendShareNotify(Long toUserId, Long fromUserId, Long shareId, 
                                String shareCode, String extractCode, String fileName) {
        StringBuilder content = new StringBuilder();
        content.append("有人向您分享了文件：").append(fileName);
        content.append("\n分享码：").append(shareCode);
        if (extractCode != null) {
            content.append("\n提取码：").append(extractCode);
        }
        
        NotifyMessage msg = NotifyMessage.create(
                NotifyMessage.Type.SHARE_RECEIVED,
                toUserId,
                "收到新的文件分享",
                content.toString()
        );
        msg.setFromUserId(fromUserId);
        msg.setBizId(shareId);
        send(msg);
    }

    /**
     * 发送存储空间预警
     */
    public void sendStorageWarning(Long userId, long usedPercent) {
        NotifyMessage msg = NotifyMessage.create(
                NotifyMessage.Type.STORAGE_WARNING,
                userId,
                "存储空间预警",
                "您的存储空间已使用 " + usedPercent + "%，请及时清理"
        );
        send(msg);
    }

    /**
     * 发送存储空间已满通知
     */
    public void sendStorageFull(Long userId) {
        NotifyMessage msg = NotifyMessage.create(
                NotifyMessage.Type.STORAGE_FULL,
                userId,
                "存储空间已满",
                "您的存储空间已满，无法上传新文件，请清理或扩容"
        );
        send(msg);
    }
}
