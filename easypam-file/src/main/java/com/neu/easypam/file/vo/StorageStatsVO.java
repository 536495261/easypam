package com.neu.easypam.file.vo;

import lombok.Data;

/**
 * 存储统计信息（展示去重效果）
 */
@Data
public class StorageStatsVO {
    
    /**
     * 物理存储记录数（实际存储的文件数）
     */
    private Long physicalFileCount;
    
    /**
     * 逻辑文件数（用户看到的文件数）
     */
    private Long logicalFileCount;
    
    /**
     * 物理存储大小（字节）
     */
    private Long physicalStorageSize;
    
    /**
     * 逻辑存储大小（如果没有去重的话）
     */
    private Long logicalStorageSize;
    
    /**
     * 节省的存储空间（字节）
     */
    private Long savedStorageSize;
    
    /**
     * 去重率（百分比）
     */
    private Double deduplicationRate;
    
    /**
     * 平均引用计数
     */
    private Double avgRefCount;
}
