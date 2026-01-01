package com.neu.easypam.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_chunk_upload")
public class ChunkUpload {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long userId;
    private Long parentId;
    private String fileName;
    private Long fileSize;
    private String fileMd5;
    private Integer chunkSize;      // 每个分片大小
    private Integer chunkCount;     // 总分片数
    private String uploadedChunks;  // 已上传分片索引，逗号分隔
    private Integer status;         // 0-上传中 1-已完成 2-已取消
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
