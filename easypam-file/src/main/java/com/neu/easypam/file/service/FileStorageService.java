package com.neu.easypam.file.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neu.easypam.file.entity.FileStorage;
import com.neu.easypam.file.vo.StorageStatsVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService extends IService<FileStorage> {
    
    /**
     * 存储文件（去重）
     * 如果相同 MD5 的文件已存在，增加引用计数
     * 否则上传到 MinIO 并创建新记录
     * 
     * @return 存储记录
     */
    FileStorage store(MultipartFile file, String md5);
    
    /**
     * 根据 MD5 查找已存在的存储
     */
    FileStorage findByMd5(String md5);
    
    /**
     * 增加引用计数
     */
    void incrementRef(Long storageId);
    
    /**
     * 减少引用计数，如果为0则删除实际文件
     * @return true 如果实际文件被删除
     */
    boolean decrementRef(Long storageId);
    
    /**
     * 获取存储统计信息（展示去重效果）
     */
    StorageStatsVO getStorageStats();
}
