package com.neu.easypam.share.controller;

import com.neu.easypam.common.dto.FileInfoDTO;
import com.neu.easypam.common.result.Result;
import com.neu.easypam.share.dto.CreateShareDTO;
import com.neu.easypam.share.dto.SaveShareDTO;
import com.neu.easypam.share.vo.PreviewVO;
import com.neu.easypam.share.vo.ShareVO;
import com.neu.easypam.share.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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
    @PostMapping
    public Result<ShareVO> createShare(
            @RequestBody CreateShareDTO dto,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(shareService.createShare(dto, userId));
    }

    @Operation(summary = "获取分享信息（公开）")
    @GetMapping("/{shareCode}")
    public Result<ShareVO> getShareInfo(@PathVariable String shareCode) {
        return Result.success(shareService.getShareInfo(shareCode));
    }

    @Operation(summary = "验证提取码（私密分享）")
    @PostMapping("/{shareCode}/verify")
    public Result<ShareVO> verifyExtractCode(
            @PathVariable String shareCode,
            @RequestParam("extractCode") String extractCode) {
        return Result.success(shareService.verifyAndGetShare(shareCode, extractCode));
    }

    @Operation(summary = "取消分享")
    @DeleteMapping("/{shareCode}")
    public Result<Void> cancelShare(
            @PathVariable String shareCode,
            @RequestHeader("X-User-Id") Long userId) {
        shareService.cancelShare(shareCode, userId);
        return Result.success();
    }

    @Operation(summary = "我的分享列表")
    @GetMapping("/list")
    public Result<List<ShareVO>> listMyShares(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(shareService.listMyShares(userId));
    }

    @Operation(summary = "获取分享文件下载链接")
    @GetMapping("/{shareCode}/download-url")
    public Result<String> getDownloadUrl(@PathVariable String shareCode) {
        return Result.success(shareService.getDownloadUrl(shareCode));
    }

    @Operation(summary = "获取分享文件预览信息")
    @GetMapping("/{shareCode}/preview")
    public Result<PreviewVO> getPreviewInfo(@PathVariable String shareCode) {
        return Result.success(shareService.getPreviewInfo(shareCode));
    }
    @Operation(summary = "保存分享到网盘")
    @PostMapping("/{shareCode}/save")
    public Result<FileInfoDTO> saveShare(@RequestHeader("X-User-Id") Long userId,
                                         @PathVariable String shareCode,@RequestBody SaveShareDTO dto) {
        return Result.success(shareService.saveShare(userId,shareCode,dto));
    }

}
