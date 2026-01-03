package com.neu.easypam.share.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 分享详情响应
 */
@Data
public class ShareVO {
    /**
     * 分享码
     */
    private String shareCode;

    /**
     * 提取码（私密分享才返回）
     */
    private String extractCode;

    /**
     * 完整分享链接
     */
    private String shareUrl;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    // ========== 文件信息（访问分享时返回）==========

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 是否是文件夹
     */
    private Integer isFolder;

    /**
     * 浏览次数
     */
    private Integer viewCount;

    /**
     * 下载次数
     */
    private Integer downloadCount;
}
