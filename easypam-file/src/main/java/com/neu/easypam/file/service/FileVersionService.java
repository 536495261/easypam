package com.neu.easypam.file.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.entity.FileVersion;
import com.neu.easypam.file.vo.FileVersionVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileVersionService extends IService<FileVersion> {

    /**
     * 上传新版本（覆盖上传）
     */
    FileInfo uploadNewVersion(Long fileId, MultipartFile file, Long userId, String remark);

    /**
     * 获取文件的所有版本
     */
    List<FileVersionVO> listVersions(Long fileId, Long userId);

    /**
     * 回滚到指定版本
     */
    FileInfo rollback(Long fileId, Integer versionNum, Long userId);

    /**
     * 获取指定版本的下载链接
     */
    String getVersionDownloadUrl(Long fileId, Integer versionNum, Long userId);
}
