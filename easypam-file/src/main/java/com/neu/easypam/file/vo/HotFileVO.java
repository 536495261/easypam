package com.neu.easypam.file.vo;

import lombok.Data;

@Data
public class HotFileVO {
    private Long fileId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Long accessCount;
}
