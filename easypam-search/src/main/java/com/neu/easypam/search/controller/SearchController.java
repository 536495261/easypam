package com.neu.easypam.search.controller;

import com.neu.easypam.common.result.Result;
import com.neu.easypam.search.service.SearchService;
import com.neu.easypam.search.vo.SearchResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "文件搜索")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "搜索文件")
    @GetMapping
    public Result<SearchResultVO> search(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "fileType", required = false) String fileType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(searchService.search(userId, keyword, fileType, page, size));
    }
}
