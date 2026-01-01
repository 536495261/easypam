package com.neu.easypam.search.service;

import com.neu.easypam.search.document.FileDocument;
import java.util.List;

public interface SearchService {
    void indexFile(FileDocument document);
    void deleteIndex(Long fileId);
    List<FileDocument> search(Long userId, String keyword, int page, int size);
}
