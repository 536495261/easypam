package com.neu.easypam.share.controller;

import com.neu.easypam.common.result.Result;
import com.neu.easypam.share.entity.ShareInfo;
import com.neu.easypam.share.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "文件分享")
@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @Operation(summary = "创建分享")
    @PostMapping("/create")
    public Result<ShareInfo> createShare(
            @RequestParam("fileId") Long fileId,
            @RequestParam(value = "shareType", defaultValue = "0") Integer shareType,
            @RequestParam(value = "expireDays", required = false) Integer expireDays,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(shareService.createShare(fileId, userId, shareType, expireDays));
    }

    @Operation(summary = "获取分享信息（公开）")
    @GetMapping("/public/{shareCode}")
    public Result<ShareInfo> getShareInfo(@PathVariable String shareCode) {
        return Result.success(shareService.getByShareCode(shareCode));
    }

    @Operation(summary = "验证提取码")
    @PostMapping("/public/{shareCode}/verify")
    public Result<Boolean> verifyExtractCode(
            @PathVariable String shareCode,
            @RequestParam("extractCode") String extractCode) {
        return Result.success(shareService.verifyExtractCode(shareCode, extractCode));
    }

    @Operation(summary = "取消分享")
    @DeleteMapping("/{shareId}")
    public Result<Void> cancelShare(
            @PathVariable Long shareId,
            @RequestHeader("X-User-Id") Long userId) {
        shareService.cancelShare(shareId, userId);
        return Result.success();
    }

    @Operation(summary = "我的分享列表")
    @GetMapping("/list")
    public Result<List<ShareInfo>> listShares(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(shareService.listUserShares(userId));
    }
}
