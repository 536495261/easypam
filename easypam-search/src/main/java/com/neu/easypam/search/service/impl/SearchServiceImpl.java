package com.neu.easypam.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.neu.easypam.search.document.FileDocument;
import com.neu.easypam.search.repository.FileDocumentRepository;
import com.neu.easypam.search.service.SearchService;
import com.neu.easypam.search.vo.SearchResultVO;
import com.neu.easypam.search.vo.SearchResultVO.SearchFileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final FileDocumentRepository fileDocumentRepository;
    private final ElasticsearchClient elasticsearchClient;

    private static final String INDEX_NAME = "easypam_file";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public SearchResultVO search(Long userId, String keyword, String fileType, int page, int size) {
        try {
            // 构建查询条件
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            
            // 必须匹配用户ID
            boolQuery.must(Query.of(q -> q.term(t -> t.field("userId").value(userId))));
            
            // 关键词搜索（文件名）
            if (keyword != null && !keyword.trim().isEmpty()) {
                boolQuery.must(Query.of(q -> q.match(m -> m.field("fileName").query(keyword))));
            }
            
            // 文件类型过滤
            if (fileType != null && !fileType.trim().isEmpty()) {
                boolQuery.filter(Query.of(q -> q.term(t -> t.field("fileType").value(fileType))));
            }

            // 执行搜索（带高亮）
            SearchResponse<FileDocument> response = elasticsearchClient.search(s -> s
                    .index(INDEX_NAME)
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    .from((page - 1) * size)
                    .size(size)
                    .highlight(h -> h
                            .fields("fileName", f -> f
                                    .preTags("<em>")
                                    .postTags("</em>")
                            )
                    )
                    .sort(sort -> sort.field(f -> f.field("createTime").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc))),
                    FileDocument.class);

            // 构建返回结果（包含高亮）
            List<SearchFileVO> files = new ArrayList<>();
            for (Hit<FileDocument> hit : response.hits().hits()) {
                FileDocument doc = hit.source();
                if (doc == null) continue;
                
                SearchFileVO vo = new SearchFileVO();
                vo.setId(doc.getId());
                vo.setUserId(doc.getUserId());
                vo.setParentId(doc.getParentId());
                vo.setFileName(doc.getFileName());
                vo.setFileType(doc.getFileType());
                vo.setContentType(doc.getContentType());
                vo.setFileSize(doc.getFileSize());
                vo.setIsFolder(doc.getIsFolder());
                vo.setCreateTime(doc.getCreateTime() != null ? doc.getCreateTime().format(FORMATTER) : null);
                vo.setUpdateTime(doc.getUpdateTime() != null ? doc.getUpdateTime().format(FORMATTER) : null);
                
                // 提取高亮结果
                Map<String, List<String>> highlights = hit.highlight();
                if (highlights != null && highlights.containsKey("fileName")) {
                    vo.setHighlightFileName(highlights.get("fileName").get(0));
                } else {
                    vo.setHighlightFileName(doc.getFileName());
                }
                
                files.add(vo);
            }

            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            SearchResultVO result = new SearchResultVO();
            result.setFiles(files);
            result.setTotal(total);
            result.setPage(page);
            result.setSize(size);
            result.setTotalPages((int) Math.ceil((double) total / size));

            return result;
        } catch (Exception e) {
            log.error("搜索失败", e);
            throw new RuntimeException("搜索失败: " + e.getMessage());
        }
    }

    @Override
    public void indexFile(FileDocument document) {
        fileDocumentRepository.save(document);
        log.info("索引文件：id={}, fileName={}", document.getId(), document.getFileName());
    }

    @Override
    public void deleteIndex(Long fileId) {
        fileDocumentRepository.deleteById(fileId);
        log.info("删除索引：id={}", fileId);
    }

    @Override
    public void batchIndex(List<FileDocument> documents) {
        fileDocumentRepository.saveAll(documents);
        log.info("批量索引：count={}", documents.size());
    }
}
