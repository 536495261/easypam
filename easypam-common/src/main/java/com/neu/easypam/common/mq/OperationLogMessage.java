package com.neu.easypam.common.mq;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 操作日志消息
 */
@Data
public class OperationLogMessage implements Serializable {

    public static final String TOPIC = "operation-log-topic";

    /**
     * 日志唯一ID，用于幂等去重
     */
    private String logId;

    private Long userId;
    private String operation;      // 操作类型
    private String targetType;     // 目标类型：FILE, FOLDER, SHARE
    private Long targetId;
    private String targetName;
    private String ip;
    private String userAgent;
    private LocalDateTime createTime;

    /**
     * 操作类型常量
     */
    public static class Operation {
        public static final String UPLOAD = "UPLOAD";
        public static final String DOWNLOAD = "DOWNLOAD";
        public static final String DELETE = "DELETE";
        public static final String RENAME = "RENAME";
        public static final String MOVE = "MOVE";
        public static final String COPY = "COPY";
        public static final String CREATE_FOLDER = "CREATE_FOLDER";
        public static final String CREATE_SHARE = "CREATE_SHARE";
        public static final String SAVE_SHARE = "SAVE_SHARE";
    }

    public static OperationLogMessage create(Long userId, String operation, 
                                              String targetType, Long targetId, String targetName) {
        OperationLogMessage msg = new OperationLogMessage();
        msg.setLogId(UUID.randomUUID().toString());
        msg.setUserId(userId);
        msg.setOperation(operation);
        msg.setTargetType(targetType);
        msg.setTargetId(targetId);
        msg.setTargetName(targetName);
        msg.setCreateTime(LocalDateTime.now());
        return msg;
    }
}
