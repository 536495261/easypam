package com.neu.easypam.file.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neu.easypam.common.mq.FileIndexMessage;
import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.service.LocalMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 文件索引消息生产者（支持顺序发送 + 失败补偿）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileIndexProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final LocalMessageService localMessageService;
    private final ObjectMapper objectMapper;

    /**
     * 发送文件创建消息
     */
    public void sendCreateMessage(FileInfo file) {
        FileIndexMessage message = buildMessage("CREATE", file);
        sendOrderly(message, file.getId());
    }

    /**
     * 发送文件更新消息
     */
    public void sendUpdateMessage(FileInfo file) {
        FileIndexMessage message = buildMessage("UPDATE", file);
        sendOrderly(message, file.getId());
    }

    /**
     * 发送文件删除消息
     */
    public void sendDeleteMessage(Long fileId) {
        FileIndexMessage message = new FileIndexMessage();
        message.setType("DELETE");
        message.setFileId(fileId);
        sendOrderly(message, fileId);
    }

    private FileIndexMessage buildMessage(String type, FileInfo file) {
        FileIndexMessage message = new FileIndexMessage();
        message.setType(type);
        message.setFileId(file.getId());
        message.setUserId(file.getUserId());
        message.setParentId(file.getParentId());
        message.setFileName(file.getFileName());
        message.setFileType(file.getFileType());
        message.setContentType(file.getContentType());
        message.setFileSize(file.getFileSize());
        message.setIsFolder(file.getIsFolder());
        message.setCreateTime(file.getCreateTime());
        message.setUpdateTime(file.getUpdateTime());
        return message;
    }

    /**
     * 顺序发送（同一 fileId 的消息进同一队列，保证顺序）
     * 发送失败时保存到本地消息表，由定时任务补偿
     */
    private void sendOrderly(FileIndexMessage message, Long fileId) {
        String hashKey = String.valueOf(fileId);
        try {
            SendResult result = rocketMQTemplate.syncSendOrderly(
                    FileIndexMessage.TOPIC, 
                    message, 
                    hashKey
            );
            if (SendStatus.SEND_OK.equals(result.getSendStatus())) {
                log.info("发送文件索引消息成功：type={}, fileId={}, msgId={}", 
                        message.getType(), fileId, result.getMsgId());
            } else {
                // 发送失败，保存到本地消息表
                log.error("发送文件索引消息失败：type={}, fileId={}, status={}", 
                        message.getType(), fileId, result.getSendStatus());
                saveToLocalMessage(message, hashKey);
            }
        } catch (Exception e) {
            log.error("发送文件索引消息异常：type={}, fileId={}", message.getType(), fileId, e);
            // 发送异常，保存到本地消息表
            saveToLocalMessage(message, hashKey);
        }
    }

    /**
     * 保存到本地消息表
     */
    private void saveToLocalMessage(FileIndexMessage message, String hashKey) {
        try {
            String messageBody = objectMapper.writeValueAsString(message);
            localMessageService.saveMessage(FileIndexMessage.TOPIC, messageBody, hashKey);
            log.info("消息已保存到本地表，等待补偿：fileId={}", message.getFileId());
        } catch (Exception e) {
            log.error("保存本地消息失败：fileId={}", message.getFileId(), e);
        }
    }

    /**
     * 重新发送消息（供定时任务调用）
     */
    public boolean resend(String messageBody, String hashKey, String messageId) {
        try {
            FileIndexMessage message = objectMapper.readValue(messageBody, FileIndexMessage.class);
            SendResult result = rocketMQTemplate.syncSendOrderly(
                    FileIndexMessage.TOPIC, 
                    message, 
                    hashKey
            );
            if (SendStatus.SEND_OK.equals(result.getSendStatus())) {
                localMessageService.markSuccess(messageId);
                log.info("补偿发送成功：messageId={}, fileId={}", messageId, message.getFileId());
                return true;
            } else {
                localMessageService.markRetry(messageId, "发送状态：" + result.getSendStatus());
                return false;
            }
        } catch (Exception e) {
            localMessageService.markRetry(messageId, e.getMessage());
            log.error("补偿发送失败：messageId={}", messageId, e);
            return false;
        }
    }
}
