package com.neu.easypam.file.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.neu.easypam.common.dto.SaveShareDTO;
import com.neu.easypam.file.entity.FileInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface FileService extends IService<FileInfo> {
    FileInfo upload(MultipartFile file, Long userId, Long parentId);
    void delete(Long fileId, Long userId);
    void rename(Long fileId, String newName, Long userId);
    List<FileInfo> listFiles(Long userId, Long parentId);
    FileInfo createFolder(String folderName, Long userId, Long parentId);
    String getDownloadUrl(Long fileId, Long userId);
    
    /**
     * 获取下载链接（自定义过期时间）
     * @param expireMinutes 过期时间（分钟）
     */
    String getDownloadUrl(Long fileId, Long userId, Integer expireMinutes);
    FileInfo quickUpload(String md5, String fileName, Long userId, Long parentId);
    void download(Long fileId, Long userId, HttpServletResponse response) throws IOException;

    void batchDownload(List<Long> fileIds, Long userId, HttpServletResponse response);

    IPage<FileInfo> listFilesByPage(Long userId,Long parentId, int page, int size, String sortBy, String sortOrder);

    /**
     * 移动文件/文件夹
     * @param fileId 文件ID
     * @param targetParentId 目标文件夹ID
     * @param userId 用户ID
     */
    void move(Long fileId, Long targetParentId, Long userId);

    /**
     * 复制文件/文件夹
     * @param fileId 文件ID
     * @param targetParentId 目标文件夹ID
     * @param userId 用户ID
     * @return 复制后的文件信息
     */
    FileInfo copy(Long fileId, Long targetParentId, Long userId);

    /**
     * 批量删除
     */
    void deleteBatch(Long[] fileIds, Long userId);

    // ========== 回收站功能 ==========

    /**
     * 移入回收站
     */
    void moveToTrash(Long fileId, Long userId);

    /**
     * 批量移入回收站
     */
    void batchMoveToTrash(Long[] fileIds, Long userId);

    /**
     * 查看回收站列表
     */
    List<FileInfo> listTrash(Long userId);

    /**
     * 从回收站恢复
     */
    void restore(Long fileId, Long userId);

    /**
     * 彻底删除（从回收站）
     */
    void deletePermanently(Long fileId, Long userId);

    /**
     * 清空回收站
     */
    void emptyTrash(Long userId);

    void downloadByShared(Long fileId, HttpServletResponse response);

    /**
     * 内部接口：获取下载链接（不校验用户权限，用于分享下载）
     */
    String getInternalDownloadUrl(Long fileId, Integer expireMinutes);

    /**
     * 内部接口：保存分享文件到网盘
     * @param saveShareDTO
     * @return
     */
    FileInfo saveShared(SaveShareDTO saveShareDTO);

    /**
     * 内部接口：获取文件夹内容（不校验用户权限，用于分享浏览）
     */
    List<FileInfo> listFolderChildren(Long folderId);

    /**
     * 内部接口：下载文件夹为ZIP（用于分享下载）
     */
    void downloadFolderAsZip(Long folderId, HttpServletResponse response);
}