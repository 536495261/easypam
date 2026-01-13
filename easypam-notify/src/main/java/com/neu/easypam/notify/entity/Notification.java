package com.neu.easypam.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_notification")
public class Notification {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 消息唯一ID，用于幂等去重
     */
    private String messageId;

    private Long userId;

    private String type;

    private String title;

    private String content;

    private Long bizId;

    private Long fromUserId;

    private Integer isRead;

    private LocalDateTime createTime;
}
