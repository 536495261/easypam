package com.neu.easypam.share.entity;

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

    private String messageId;
    private String topic;
    private String messageBody;
    private String hashKey;
    private Integer status;
    private Integer retryCount;
    private Integer maxRetry;
    private LocalDateTime nextRetryTime;
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static class Status {
        public static final int PENDING = 0;
        public static final int SUCCESS = 1;
        public static final int FAILED = 2;
        public static final int PROCESSING = 3;  // 处理中（防止并发）
    }
}
