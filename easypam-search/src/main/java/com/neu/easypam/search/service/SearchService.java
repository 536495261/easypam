package com.neu.easypam.search.service;

import com.neu.easypam.search.document.FileDocument;
import com.neu.easypam.search.vo.SearchResultVO;

import java.util.List;

public interface SearchService {
    
    /**
     * 搜索文件
     * @param userId 用户ID
     * @param keyword 关键词
     * @param fileType 文件类型（可选）
     * @param page 页码
     * @param size 每页大小
     */
    SearchResultVO search(Long userId, String keyword, String fileType, int page, int size);
    
    /**
     * 索引文件（新增/更新）
     */
    void indexFile(FileDocument document);
    
    /**
     * 删除索引
     */
    void deleteIndex(Long fileId);
    
    /**
     * 批量索引
     */
    void batchIndex(List<FileDocument> documents);
}
