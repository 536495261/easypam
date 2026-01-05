package com.neu.easypam.common.feign;

import com.neu.easypam.common.dto.FileInfoDTO;
import com.neu.easypam.common.dto.SaveShareDTO;
import com.neu.easypam.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "easypam-file", path = "/file")
public interface FileFeignClient {

    /**
     * 内部接口：根据ID获取文件信息
     */
    @GetMapping("/internal/{fileId}")
    Result<FileInfoDTO> getFileById(@PathVariable("fileId") Long fileId);

    /**
     * 内部接口：校验文件是否属于指定用户
     */
    @GetMapping("/internal/{fileId}/check")
    Result<FileInfoDTO> checkFileOwner(
            @PathVariable("fileId") Long fileId,
            @RequestHeader("X-User-Id") Long userId);

    /**
     * 内部接口：获取文件下载链接（用于分享下载）
     */
    @GetMapping("/internal/{fileId}/download-url")
    Result<String> getShareDownloadUrl(@PathVariable("fileId") Long fileId);

    @PostMapping("/internal/save-shared")
    Result<FileInfoDTO> saveShareFile(@RequestBody SaveShareDTO dto);

    /**
     * 内部接口：获取文件夹内容（用于分享浏览）
     */
    @GetMapping("/internal/{folderId}/children")
    Result<java.util.List<FileInfoDTO>> listFolderChildren(@PathVariable("folderId") Long folderId);
}
