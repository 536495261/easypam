package com.neu.easypam.storage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_user_storage")
public class UserStorage {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long userId;
    private Long totalSpace;    // 总空间（字节）
    private Long usedSpace;     // 已用空间（字节）
    private Integer level;      // 用户等级，影响存储空间
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
