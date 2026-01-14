package com.neu.easypam.file.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileVersionVO {
    private Long id;
    private Integer versionNum;
    private Long fileSize;
    private String md5;
    private String remark;
    private LocalDateTime createTime;
    private Boolean isCurrent;  // 是否为当前版本
}
