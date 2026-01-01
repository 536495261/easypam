package com.neu.easypam.storage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "存储空间统计")
public class StorageStatsVO {
    
    @Schema(description = "总空间（字节）")
    private Long totalSpace;
    
    @Schema(description = "已用空间（字节）")
    private Long usedSpace;
    
    @Schema(description = "剩余空间（字节）")
    private Long freeSpace;
    
    @Schema(description = "使用率（百分比）")
    private Double usagePercent;
    
    @Schema(description = "总空间（格式化）")
    private String totalSpaceText;
    
    @Schema(description = "已用空间（格式化）")
    private String usedSpaceText;
    
    @Schema(description = "剩余空间（格式化）")
    private String freeSpaceText;
    
    @Schema(description = "用户等级")
    private Integer level;
}
