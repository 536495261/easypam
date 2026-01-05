package com.neu.easypam.search.repository;

import com.neu.easypam.search.document.FileDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileDocumentRepository extends ElasticsearchRepository<FileDocument, Long> {
    
    /**
     * 根据用户ID和文件名模糊搜索
     */
    List<FileDocument> findByUserIdAndFileNameContaining(Long userId, String fileName);
    
    /**
     * 根据用户ID删除所有文档
     */
    void deleteByUserId(Long userId);
}
