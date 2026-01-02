package com.neu.easypam.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_file")
public class FileInfo {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long userId;
    private Long parentId;      // 父文件夹ID，0表示根目录
    private String fileName;
    private String filePath;    // MinIO存储路径
    private Long fileSize;
    private String fileType;    // 文件类型：folder/image/video/audio/document/other
    private String contentType; // MIME类型
    private String md5;         // 文件MD5，用于秒传
    private Integer isFolder;   // 0-文件 1-文件夹
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    private Integer deleted;    // 0-正常 1-回收站
    
    private LocalDateTime deleteTime;  // 删除时间（移入回收站时间）
}
