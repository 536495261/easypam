package com.neu.easypam.search.document;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * ES文件索引文档
 */
@Data
public class FileDocument {
    private Long id;
    private Long userId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String content;     // 文件内容（文本类文件）
    private LocalDateTime createTime;
}
