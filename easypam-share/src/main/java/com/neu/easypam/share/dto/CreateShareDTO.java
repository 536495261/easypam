package com.neu.easypam.share.dto;

import lombok.Data;

/**
 * 创建分享请求
 */
@Data
public class CreateShareDTO {
    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 分享类型：0-公开 1-私密
     */
    private Integer shareType = 0;

    /**
     * 过期天数，null表示永久有效
     */
    private Integer expireDays;
}
