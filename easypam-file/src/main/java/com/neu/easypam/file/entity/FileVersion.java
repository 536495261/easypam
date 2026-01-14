package com.neu.easypam.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件版本表
 * 记录文件的历史版本，支持版本回滚
 */
@Data
@TableName("t_file_version")
public class FileVersion {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联的文件ID
     */
    private Long fileId;

    /**
     * 版本号（从1开始递增）
     */
    private Integer versionNum;

    /**
     * 存储ID（关联 t_file_storage）
     */
    private Long storageId;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * MD5
     */
    private String md5;

    /**
     * 版本备注
     */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
