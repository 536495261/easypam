package com.neu.easypam.share.vo;

import lombok.Data;

/**
 * 文件预览响应
 */
@Data
public class PreviewVO {
    /**
     * 预览链接（MinIO预签名URL）
     */
    private String previewUrl;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件类型：image/video/audio/document/other
     */
    private String fileType;

    /**
     * MIME类型
     */
    private String contentType;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 是否支持预览
     */
    private Boolean previewable;

    /**
     * 预览方式提示：img/video/audio/iframe/text/unsupported
     */
    private String previewType;
}
