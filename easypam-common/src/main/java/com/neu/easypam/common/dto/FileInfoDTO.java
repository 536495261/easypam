package com.neu.easypam.common.dto;

import lombok.Data;

/**
 * 文件信息DTO（用于跨服务传输）
 */
@Data
public class FileInfoDTO {
    private Long id;
    private Long userId;
    private Long parentId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private String contentType;
    private Integer isFolder;
    private Integer deleted;
}
