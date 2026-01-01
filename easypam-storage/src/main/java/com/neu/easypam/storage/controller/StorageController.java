package com.neu.easypam.storage.controller;

import com.neu.easypam.common.result.Result;
import com.neu.easypam.storage.entity.UserStorage;
import com.neu.easypam.storage.service.StorageService;
import com.neu.easypam.storage.vo.StorageStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "存储空间管理")
@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @Operation(summary = "获取存储空间信息")
    @GetMapping("/info")
    public Result<UserStorage> getStorageInfo(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(storageService.getOrCreateByUserId(userId));
    }

    @Operation(summary = "获取存储空间统计")
    @GetMapping("/stats")
    public Result<StorageStatsVO> getStorageStats(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(storageService.getStorageStats(userId));
    }

    @Operation(summary = "检查空间是否足够")
    @GetMapping("/check")
    public Result<Boolean> checkSpace(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("size") Long fileSize) {
        return Result.success(storageService.checkSpace(userId, fileSize));
    }

    @Operation(summary = "校验空间（空间不足抛异常）")
    @PostMapping("/validate")
    public Result<Void> validateSpace(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("size") Long fileSize) {
        storageService.validateSpace(userId, fileSize);
        return Result.success();
    }

    @Operation(summary = "增加已用空间")
    @PostMapping("/add-used")
    public Result<Void> addUsedSpace(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("size") Long fileSize) {
        storageService.addUsedSpace(userId, fileSize);
        return Result.success();
    }

    @Operation(summary = "减少已用空间")
    @PostMapping("/reduce-used")
    public Result<Void> reduceUsedSpace(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("size") Long fileSize) {
        storageService.reduceUsedSpace(userId, fileSize);
        return Result.success();
    }

}
