package com.neu.easypam.file.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.neu.easypam.common.result.Result;
import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Tag(name = "文件管理")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public Result<FileInfo> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentId", defaultValue = "0") Long parentId,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(fileService.upload(file, userId, parentId));
    }

    @Operation(summary = "秒传检测")
    @PostMapping("/quick-upload")
    public Result<FileInfo> quickUpload(
            @RequestParam("md5") String md5,
            @RequestParam("fileName") String fileName,
            @RequestParam(value = "parentId", defaultValue = "0") Long parentId,
            @RequestHeader("X-User-Id") Long userId) {
        FileInfo fileInfo = fileService.quickUpload(md5, fileName, userId, parentId);
        return fileInfo != null ? Result.success(fileInfo) : Result.error("文件不存在，请上传");
    }

    @Operation(summary = "创建文件夹")
    @PostMapping("/folder")
    public Result<FileInfo> createFolder(
            @RequestParam("name") String name,
            @RequestParam(value = "parentId", defaultValue = "0") Long parentId,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(fileService.createFolder(name, userId, parentId));
    }

    @Operation(summary = "获取文件列表")
    @GetMapping("/list")
    public Result<List<FileInfo>> list(
            @RequestParam(value = "parentId", defaultValue = "0") Long parentId,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(fileService.listFiles(userId, parentId));
    }
    @Operation(summary = "获取文件列表（包含分页）")
    @GetMapping("/list/page")
    public Result<IPage<FileInfo>> listPage(@RequestHeader("X-User-Id") Long userId,@RequestParam(value = "parentId",defaultValue = "0") Long parentId
            ,@RequestParam(value = "page",defaultValue = "1")int page,@RequestParam(value ="size",defaultValue = "10") int size
            ,@RequestParam(value = "sortBy",defaultValue = "createTime") String sortBy
            ,@RequestParam(value = "sortOrder",defaultValue = "desc") String sortOrder) {
        return Result.success(fileService.listFilesByPage(userId,parentId,page,size,sortBy,sortOrder));
    }
    @Operation(summary = "删除文件")
    @DeleteMapping("/{fileId}")
    public Result<Void> delete(
            @PathVariable Long fileId,
            @RequestHeader("X-User-Id") Long userId) {
        fileService.delete(fileId, userId);
        return Result.success();
    }

    @Operation(summary = "重命名")
    @PutMapping("/{fileId}/rename")
    public Result<Void> rename(
            @PathVariable Long fileId,
            @RequestParam("name") String name,
            @RequestHeader("X-User-Id") Long userId) {
        fileService.rename(fileId, name, userId);
        return Result.success();
    }

    @Operation(summary = "获取下载链接")
    @GetMapping("/{fileId}/download-url")
    public Result<String> getDownloadUrl(
            @PathVariable Long fileId,
            @RequestParam(value = "expireMinutes", required = false) Integer expireMinutes,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(fileService.getDownloadUrl(fileId, userId, expireMinutes));
    }

    @Operation(summary = "流式下载文件")
    @GetMapping("/{fileId}/download")
    public void download(
            @PathVariable Long fileId,
            @RequestHeader("X-User-Id") Long userId,
            HttpServletResponse response) throws IOException {
        fileService.download(fileId, userId, response);
    }

    @Operation(summary = "批量下载")
    @PostMapping("/batch-download")
    public void batchDownload(@RequestBody List<Long> fileIds,
                              @RequestHeader("X-User-Id") Long userId,
                              HttpServletResponse response) throws IOException {
        fileService.batchDownload(fileIds,userId,response);
    }
}
