package com.neu.easypam.search.vo;

import com.neu.easypam.search.document.FileDocument;
import lombok.Data;

import java.util.List;

@Data
public class SearchResultVO {
    private List<FileDocument> files;
    private long total;
    private int page;
    private int size;
    private int totalPages;
}
