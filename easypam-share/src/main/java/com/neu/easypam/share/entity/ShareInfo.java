package com.neu.easypam.share.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_share")
public class ShareInfo {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long userId;
    private Long fileId;
    private String shareCode;       // 分享码（短链接）
    private String extractCode;     // 提取码
    private Integer shareType;      // 0-公开 1-私密
    private LocalDateTime expireTime; // 过期时间，null表示永久
    private Integer viewCount;      // 浏览次数
    private Integer downloadCount;  // 下载次数
    private Integer status;         // 0-已取消 1-有效
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
