package com.neu.easypam.file.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neu.easypam.file.entity.ChunkUpload;
import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.vo.ChunkInitVO;
import org.springframework.web.multipart.MultipartFile;

public interface ChunkUploadService extends IService<ChunkUpload> {
    
    /**
     * 初始化分片上传
     */
    ChunkInitVO initUpload(Long userId, Long parentId, String fileName, Long fileSize, String fileMd5, Integer chunkSize);
    
    /**
     * 上传分片
     */
    void uploadChunk(Long uploadId, Integer chunkIndex, MultipartFile file, Long userId);
    
    /**
     * 合并分片
     */
    FileInfo mergeChunks(Long uploadId, Long userId);
    
    /**
     * 获取上传状态（已上传的分片）
     */
    ChunkInitVO getUploadStatus(Long uploadId, Long userId);
}
