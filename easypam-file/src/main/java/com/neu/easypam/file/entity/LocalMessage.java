package com.neu.easypam.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表（用于消息发送失败补偿）
 */
@Data
@TableName("t_local_message")
public class LocalMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 消息唯一ID
     */
    private String messageId;

    /**
     * 消息主题
     */
    private String topic;

    /**
     * 消息内容JSON
     */
    private String messageBody;

    /**
     * 顺序消息的hashKey
     */
    private String hashKey;

    /**
     * 状态：0-待发送 1-发送成功 2-发送失败
     */
    private Integer status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetry;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;

    /**
     * 错误信息
     */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 状态常量
     */
    public static class Status {
        public static final int PENDING = 0;     // 待发送
        public static final int PROCESSING = 3;  // 处理中（防止并发）
        public static final int SUCCESS = 1;     // 发送成功
        public static final int FAILED = 2;      // 发送失败（超过最大重试次数）
    }
}
