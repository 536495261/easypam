package com.neu.easypam.common.mq;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 通知消息
 */
@Data
public class NotifyMessage implements Serializable {

    public static final String TOPIC = "notify-topic";

    /**
     * 消息唯一ID，用于幂等去重
     */
    private String messageId;

    /**
     * 通知类型
     */
    private String type;

    /**
     * 接收用户ID
     */
    private Long userId;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 关联业务ID（如分享ID、文件ID等）
     */
    private Long bizId;

    /**
     * 发送者用户ID（可选）
     */
    private Long fromUserId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 通知类型常量
     */
    public static class Type {
        public static final String SHARE_RECEIVED = "SHARE_RECEIVED";      // 收到分享
        public static final String STORAGE_WARNING = "STORAGE_WARNING";    // 存储空间预警
        public static final String STORAGE_FULL = "STORAGE_FULL";          // 存储空间已满
    }

    public static NotifyMessage create(String type, Long userId, String title, String content) {
        NotifyMessage msg = new NotifyMessage();
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setType(type);
        msg.setUserId(userId);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setCreateTime(LocalDateTime.now());
        return msg;
    }
}
