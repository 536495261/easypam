package com.neu.easypam.file.controller;

import com.neu.easypam.common.result.Result;
import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.service.ChunkUploadService;
import com.neu.easypam.file.vo.ChunkInitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "分片上传")
@RestController
@RequestMapping("/file/chunk")
@RequiredArgsConstructor
public class ChunkUploadController {

    private final ChunkUploadService chunkUploadService;

    @Operation(summary = "初始化分片上传")
    @PostMapping("/init")
    public Result<ChunkInitVO> initUpload(
            @RequestParam("fileName") String fileName,
            @RequestParam("fileSize") Long fileSize,
            @RequestParam("fileMd5") String fileMd5,
            @RequestParam(value = "parentId", defaultValue = "0") Long parentId,
            @RequestParam(value = "chunkSize", required = false) Integer chunkSize,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(chunkUploadService.initUpload(userId, parentId, fileName, fileSize, fileMd5, chunkSize));
    }

    @Operation(summary = "上传分片")
    @PostMapping("/upload")
    public Result<Void> uploadChunk(
            @RequestParam("uploadId") Long uploadId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-User-Id") Long userId) {
        chunkUploadService.uploadChunk(uploadId, chunkIndex, file, userId);
        return Result.success();
    }

    @Operation(summary = "合并分片")
    @PostMapping("/merge")
    public Result<FileInfo> mergeChunks(
            @RequestParam("uploadId") Long uploadId,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(chunkUploadService.mergeChunks(uploadId, userId));
    }

    @Operation(summary = "获取上传状态")
    @GetMapping("/status")
    public Result<ChunkInitVO> getUploadStatus(
            @RequestParam("uploadId") Long uploadId,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(chunkUploadService.getUploadStatus(uploadId, userId));
    }
}
