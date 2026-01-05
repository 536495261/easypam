package com.neu.easypam.common.mq;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件索引消息（用于同步到 ES）
 */
@Data
public class FileIndexMessage implements Serializable {
    
    public static final String TOPIC = "file-index-topic";
    
    /**
     * 消息类型：CREATE, UPDATE, DELETE
     */
    private String type;
    
    private Long fileId;
    private Long userId;
    private Long parentId;
    private String fileName;
    private String fileType;
    private String contentType;
    private Long fileSize;
    private Integer isFolder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    public static FileIndexMessage create(String type, Long fileId) {
        FileIndexMessage msg = new FileIndexMessage();
        msg.setType(type);
        msg.setFileId(fileId);
        return msg;
    }
}
