package com.neu.easypam.file.controller;

import com.neu.easypam.common.result.Result;
import com.neu.easypam.file.service.FileCacheService;
import com.neu.easypam.file.vo.CacheStatsVO;
import com.neu.easypam.file.vo.HotFileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "缓存管理")
@RestController
@RequestMapping("/file/cache")
@RequiredArgsConstructor
public class FileCacheController {

    private final FileCacheService fileCacheService;

    @Operation(summary = "获取热点文件列表")
    @GetMapping("/hot")
    public Result<List<HotFileVO>> getHotFiles(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(fileCacheService.getHotFiles(limit));
    }

    @Operation(summary = "获取缓存统计")
    @GetMapping("/stats")
    public Result<CacheStatsVO> getCacheStats() {
        return Result.success(fileCacheService.getCacheStats());
    }
}
