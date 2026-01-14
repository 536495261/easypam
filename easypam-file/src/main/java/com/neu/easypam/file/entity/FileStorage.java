package com.neu.easypam.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文件存储表（内容寻址存储）
 * 相同内容的文件只存储一份，通过引用计数管理生命周期
 */
@Data
@TableName("t_file_storage")
public class FileStorage {
    
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 文件内容的 MD5 哈希值（内容寻址的 key）
     */
    private String md5;
    
    /**
     * MinIO 存储路径
     */
    private String storagePath;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * MIME 类型
     */
    private String contentType;
    
    /**
     * 引用计数：有多少个用户文件引用此存储
     * 当 refCount = 0 时，可以安全删除 MinIO 中的文件
     */
    private Integer refCount;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
