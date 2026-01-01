package com.neu.easypam.search.controller;

import com.neu.easypam.common.result.Result;
import com.neu.easypam.search.document.FileDocument;
import com.neu.easypam.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "文件检索")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "搜索文件")
    @GetMapping
    public Result<List<FileDocument>> search(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(searchService.search(userId, keyword, page, size));
    }
}
