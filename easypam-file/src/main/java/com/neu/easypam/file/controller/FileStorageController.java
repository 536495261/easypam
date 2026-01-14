package com.neu.easypam.file.controller;

import com.neu.easypam.common.result.Result;
import com.neu.easypam.file.service.FileStorageService;
import com.neu.easypam.file.vo.StorageStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "存储管理")
@RestController
@RequestMapping("/file/storage")
@RequiredArgsConstructor
public class FileStorageController {
    
    private final FileStorageService fileStorageService;
    
    @Operation(summary = "获取存储统计（展示去重效果）")
    @GetMapping("/stats")
    public Result<StorageStatsVO> getStorageStats() {
        return Result.success(fileStorageService.getStorageStats());
    }
}
