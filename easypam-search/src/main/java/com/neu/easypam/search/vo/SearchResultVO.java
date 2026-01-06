package com.neu.easypam.search.vo;

import com.neu.easypam.search.document.FileDocument;
import lombok.Data;

import java.util.List;

@Data
public class SearchResultVO {
    private List<SearchFileVO> files;
    private long total;
    private int page;
    private int size;
    private int totalPages;

    /**
     * 搜索结果文件（包含高亮）
     */
    @Data
    public static class SearchFileVO {
        private Long id;
        private Long userId;
        private Long parentId;
        private String fileName;
        private String highlightFileName;  // 高亮后的文件名
        private String fileType;
        private String contentType;
        private Long fileSize;
        private Integer isFolder;
        private String createTime;
        private String updateTime;
    }
}
