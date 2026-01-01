package com.neu.easypam.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "分片上传初始化响应")
public class ChunkInitVO {
    
    @Schema(description = "上传任务ID")
    private Long uploadId;
    
    @Schema(description = "是否秒传成功")
    private Boolean quickUpload;
    
    @Schema(description = "秒传成功时的文件信息")
    private Long fileId;
    
    @Schema(description = "已上传的分片索引列表（用于断点续传）")
    private List<Integer> uploadedChunks;
    
    @Schema(description = "总分片数")
    private Integer chunkCount;
}
