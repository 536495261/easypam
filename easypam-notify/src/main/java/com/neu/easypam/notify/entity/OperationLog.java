package com.neu.easypam.notify.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_operation_log")
public class OperationLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long userId;
    private String operation;   // 操作类型：upload/download/delete/share等
    private String targetType;  // 目标类型：file/folder
    private Long targetId;
    private String targetName;
    private String ip;
    private String userAgent;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
