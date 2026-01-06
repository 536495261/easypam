package com.neu.easypam.search.controller;

import com.neu.easypam.common.result.Result;
import com.neu.easypam.search.service.SearchHistoryService;
import com.neu.easypam.search.service.SearchService;
import com.neu.easypam.search.vo.SearchResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "文件搜索")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final SearchHistoryService searchHistoryService;

    @Operation(summary = "搜索文件")
    @GetMapping
    public Result<SearchResultVO> search(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "fileType", required = false) String fileType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        // 保存搜索历史
        searchHistoryService.save(userId, keyword);
        return Result.success(searchService.search(userId, keyword, fileType, page, size));
    }

    @Operation(summary = "获取搜索历史")
    @GetMapping("/history")
    public Result<List<String>> getHistory(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return Result.success(searchHistoryService.list(userId, limit));
    }

    @Operation(summary = "删除单条搜索历史")
    @DeleteMapping("/history")
    public Result<Void> deleteHistory(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("keyword") String keyword) {
        searchHistoryService.delete(userId, keyword);
        return Result.success();
    }

    @Operation(summary = "清空搜索历史")
    @DeleteMapping("/history/all")
    public Result<Void> clearHistory(@RequestHeader("X-User-Id") Long userId) {
        searchHistoryService.clear(userId);
        return Result.success();
    }
}
