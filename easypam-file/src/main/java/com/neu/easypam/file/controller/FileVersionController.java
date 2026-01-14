package com.neu.easypam.file.controller;

import com.neu.easypam.common.result.Result;
import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.service.FileVersionService;
import com.neu.easypam.file.vo.FileVersionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "文件版本管理")
@RestController
@RequestMapping("/file/version")
@RequiredArgsConstructor
public class FileVersionController {

    private final FileVersionService fileVersionService;

    @Operation(summary = "上传新版本（覆盖上传）")
    @PostMapping("/{fileId}")
    public Result<FileInfo> uploadNewVersion(
            @PathVariable Long fileId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(fileVersionService.uploadNewVersion(fileId, file, userId, remark));
    }

    @Operation(summary = "获取文件版本列表")
    @GetMapping("/{fileId}")
    public Result<List<FileVersionVO>> listVersions(
            @PathVariable Long fileId,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(fileVersionService.listVersions(fileId, userId));
    }

    @Operation(summary = "回滚到指定版本")
    @PostMapping("/{fileId}/rollback/{versionNum}")
    public Result<FileInfo> rollback(
            @PathVariable Long fileId,
            @PathVariable Integer versionNum,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(fileVersionService.rollback(fileId, versionNum, userId));
    }

    @Operation(summary = "获取指定版本下载链接")
    @GetMapping("/{fileId}/download/{versionNum}")
    public Result<String> getVersionDownloadUrl(
            @PathVariable Long fileId,
            @PathVariable Integer versionNum,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(fileVersionService.getVersionDownloadUrl(fileId, versionNum, userId));
    }
}
