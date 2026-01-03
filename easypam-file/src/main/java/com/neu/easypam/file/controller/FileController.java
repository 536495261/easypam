package com.neu.easypam.file.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.neu.easypam.common.result.Result;
import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.simpleframework.xml.Path;
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

    @Operation(summary = "批量删除文件")
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(
            @RequestBody Long[] fileIds,
            @RequestHeader("X-User-Id") Long userId) {
        fileService.deleteBatch(fileIds, userId);
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

    @Operation(summary = "移动文件/文件夹")
    @PutMapping("/{fileId}/move")
    public Result<Void> move(
            @PathVariable Long fileId,
            @RequestParam("targetParentId") Long targetParentId,
            @RequestHeader("X-User-Id") Long userId) {
        fileService.move(fileId, targetParentId, userId);
        return Result.success();
    }

    @Operation(summary = "复制文件/文件夹")
    @PostMapping("/{fileId}/copy")
    public Result<FileInfo> copy(
            @PathVariable Long fileId,
            @RequestParam("targetParentId") Long targetParentId,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(fileService.copy(fileId, targetParentId, userId));
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

    // ========== 回收站接口 ==========

    @Operation(summary = "移入回收站")
    @PostMapping("/{fileId}/trash")
    public Result<Void> moveToTrash(
            @PathVariable Long fileId,
            @RequestHeader("X-User-Id") Long userId) {
        fileService.moveToTrash(fileId, userId);
        return Result.success();
    }

    @Operation(summary = "批量移入回收站")
    @PostMapping("/batch-trash")
    public Result<Void> batchMoveToTrash(
            @RequestBody Long[] fileIds,
            @RequestHeader("X-User-Id") Long userId) {
        fileService.batchMoveToTrash(fileIds, userId);
        return Result.success();
    }

    @Operation(summary = "查看回收站列表")
    @GetMapping("/trash")
    public Result<List<FileInfo>> listTrash(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(fileService.listTrash(userId));
    }

    @Operation(summary = "从回收站恢复")
    @PostMapping("/{fileId}/restore")
    public Result<Void> restore(
            @PathVariable Long fileId,
            @RequestHeader("X-User-Id") Long userId) {
        fileService.restore(fileId, userId);
        return Result.success();
    }

    @Operation(summary = "彻底删除")
    @DeleteMapping("/{fileId}/permanent")
    public Result<Void> deletePermanently(
            @PathVariable Long fileId,
            @RequestHeader("X-User-Id") Long userId) {
        fileService.deletePermanently(fileId, userId);
        return Result.success();
    }

    @Operation(summary = "清空回收站")
    @DeleteMapping("/trash/empty")
    public Result<Void> emptyTrash(@RequestHeader("X-User-Id") Long userId) {
        fileService.emptyTrash(userId);
        return Result.success();
    }

    // ========== 内部接口（供其他服务调用）==========

    @Operation(summary = "内部接口：获取文件信息")
    @GetMapping("/internal/{fileId}")
    public Result<FileInfo> getFileById(@PathVariable Long fileId) {
        FileInfo file = fileService.getById(fileId);
        if (file == null || file.getDeleted() == 1) {
            return Result.error("文件不存在");
        }
        return Result.success(file);
    }

    @Operation(summary = "内部接口：校验文件归属")
    @GetMapping("/internal/{fileId}/check")
    public Result<FileInfo> checkFileOwner(
            @PathVariable Long fileId,
            @RequestHeader("X-User-Id") Long userId) {
        FileInfo file = fileService.getById(fileId);
        if (file == null || file.getDeleted() == 1) {
            return Result.error("文件不存在");
        }
        if (!file.getUserId().equals(userId)) {
            return Result.error("无权限访问该文件");
        }
        return Result.success(file);
    }
    @Operation(summary = "内部接口：下载分享文件")
    @GetMapping("/internal/{fileId}/download")
    public Result<String> downloadByShared(@PathVariable("fileId") Long fileId,HttpServletResponse response){
        fileService.downloadByShared(fileId,response);
        return Result.success("下载成功");
    }

    @Operation(summary = "内部接口：获取下载链接（用于分享）")
    @GetMapping("/internal/{fileId}/download-url")
    public Result<String> getShareDownloadUrl(@PathVariable Long fileId) {
        FileInfo file = fileService.getById(fileId);
        if (file == null || file.getDeleted() == 1) {
            return Result.error("文件不存在");
        }
        if (file.getIsFolder() == 1) {
            return Result.error("文件夹不支持下载");
        }
        // 生成60分钟有效的下载链接
        String url = fileService.getInternalDownloadUrl(fileId, 60);
        return Result.success(url);
    }

}
