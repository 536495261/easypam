package com.neu.easypam.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 通知消息生产者（同步发送 + 失败补偿）
 */
@Slf4j
@Component
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnProperty(name = "rocketmq.producer.group")
public class NotifyProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    
    // 可选注入：只有实现了 LocalMessageHandler 的服务才有补偿能力
    @Autowired(required = false)
    private LocalMessageHandler localMessageHandler;

    public NotifyProducer(RocketMQTemplate rocketMQTemplate, ObjectMapper objectMapper) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送通知消息（同步发送，失败时保存到本地消息表）
     */
    public void send(NotifyMessage message) {
        try {
            SendResult result = rocketMQTemplate.syncSend(NotifyMessage.TOPIC, message, 3000);
            if (SendStatus.SEND_OK.equals(result.getSendStatus())) {
                log.info("发送通知消息成功：messageId={}, type={}, userId={}", 
                        message.getMessageId(), message.getType(), message.getUserId());
            } else {
                log.error("发送通知消息失败：messageId={}, status={}", 
                        message.getMessageId(), result.getSendStatus());
                saveToLocal(message);
            }
        } catch (Exception e) {
            log.error("发送通知消息异常：messageId={}", message.getMessageId(), e);
            saveToLocal(message);
        }
    }

    /**
     * 保存到本地消息表
     */
    private void saveToLocal(NotifyMessage message) {
        if (localMessageHandler == null) {
            log.warn("未配置LocalMessageHandler，通知消息无法补偿：messageId={}", message.getMessageId());
            return;
        }
        try {
            String messageBody = objectMapper.writeValueAsString(message);
            localMessageHandler.saveFailedMessage(NotifyMessage.TOPIC, messageBody, null);
            log.info("通知消息已保存到本地表，等待补偿：messageId={}", message.getMessageId());
        } catch (Exception e) {
            log.error("保存本地消息失败：messageId={}", message.getMessageId(), e);
        }
    }

    /**
     * 重新发送消息（供定时任务调用）
     */
    public boolean resend(String messageBody, String messageId) {
        try {
            NotifyMessage message = objectMapper.readValue(messageBody, NotifyMessage.class);
            SendResult result = rocketMQTemplate.syncSend(NotifyMessage.TOPIC, message, 3000);
            if (SendStatus.SEND_OK.equals(result.getSendStatus())) {
                if (localMessageHandler != null) {
                    localMessageHandler.markSuccess(messageId);
                }
                log.info("补偿发送通知成功：messageId={}", messageId);
                return true;
            } else {
                if (localMessageHandler != null) {
                    localMessageHandler.markRetry(messageId, "发送状态：" + result.getSendStatus());
                }
                return false;
            }
        } catch (Exception e) {
            if (localMessageHandler != null) {
                localMessageHandler.markRetry(messageId, e.getMessage());
            }
            log.error("补偿发送通知失败：messageId={}", messageId, e);
            return false;
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
