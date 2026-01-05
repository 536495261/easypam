package com.neu.easypam.file.mq;

import com.neu.easypam.common.mq.FileIndexMessage;
import com.neu.easypam.file.entity.FileInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 文件索引消息生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileIndexProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送文件创建消息
     */
    public void sendCreateMessage(FileInfo file) {
        FileIndexMessage message = buildMessage("CREATE", file);
        send(message);
    }

    /**
     * 发送文件更新消息
     */
    public void sendUpdateMessage(FileInfo file) {
        FileIndexMessage message = buildMessage("UPDATE", file);
        send(message);
    }

    /**
     * 发送文件删除消息
     */
    public void sendDeleteMessage(Long fileId) {
        FileIndexMessage message = new FileIndexMessage();
        message.setType("DELETE");
        message.setFileId(fileId);
        send(message);
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

    private void send(FileIndexMessage message) {
        try {
            rocketMQTemplate.convertAndSend(FileIndexMessage.TOPIC, message);
            log.info("发送文件索引消息：type={}, fileId={}", message.getType(), message.getFileId());
        } catch (Exception e) {
            log.error("发送文件索引消息失败", e);
        }
    }
}
