package com.neu.easypam.file.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neu.easypam.common.exception.BusinessException;
import com.neu.easypam.common.feign.StorageFeignClient;
import com.neu.easypam.file.config.MinioConfig;
import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.entity.FileStorage;
import com.neu.easypam.file.entity.FileVersion;
import com.neu.easypam.file.mapper.FileVersionMapper;
import com.neu.easypam.file.service.FileService;
import com.neu.easypam.file.service.FileStorageService;
import com.neu.easypam.file.service.FileVersionService;
import com.neu.easypam.file.vo.FileVersionVO;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileVersionServiceImpl extends ServiceImpl<FileVersionMapper, FileVersion>
        implements FileVersionService {

    private final FileService fileService;
    private final FileStorageService fileStorageService;
    private final StorageFeignClient storageFeignClient;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    private static final int MAX_VERSIONS = 10;  // 最多保留10个版本

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo uploadNewVersion(Long fileId, MultipartFile file, Long userId, String remark) {
        // 1. 校验文件存在且有权限
        FileInfo fileInfo = fileService.getById(fileId);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId) || fileInfo.getDeleted() == 1) {
            throw new BusinessException("文件不存在或无权限");
        }
        if (fileInfo.getIsFolder() == 1) {
            throw new BusinessException("文件夹不支持版本控制");
        }

        // 2. 校验存储空间
        storageFeignClient.validateSpace(userId, file.getSize());

        try {
            // 3. 保存当前版本到历史
            saveCurrentAsVersion(fileInfo, remark);

            // 4. 上传新文件到存储
            String md5 = DigestUtil.md5Hex(file.getInputStream());
            FileStorage newStorage = fileStorageService.store(file, md5);

            // 5. 减少旧存储的引用计数
            if (fileInfo.getStorageId() != null) {
                fileStorageService.decrementRef(fileInfo.getStorageId());
            }

            // 6. 更新文件信息
            fileInfo.setStorageId(newStorage.getId());
            fileInfo.setFilePath(newStorage.getStoragePath());
            fileInfo.setFileSize(newStorage.getFileSize());
            fileInfo.setMd5(newStorage.getMd5());
            fileInfo.setContentType(file.getContentType());
            fileService.updateById(fileInfo);

            // 7. 更新存储空间（新版本大小 - 旧版本大小）
            long sizeDiff = file.getSize() - fileInfo.getFileSize();
            if (sizeDiff > 0) {
                storageFeignClient.addUsedSpace(userId, sizeDiff);
            } else if (sizeDiff < 0) {
                storageFeignClient.reduceUsedSpace(userId, -sizeDiff);
            }

            // 8. 清理超出限制的旧版本
            cleanupOldVersions(fileId);

            log.info("用户{}上传文件新版本：fileId={}", userId, fileId);
            return fileInfo;
        } catch (Exception e) {
            throw new BusinessException("上传新版本失败：" + e.getMessage());
        }
    }

    /**
     * 保存当前版本到历史
     */
    private void saveCurrentAsVersion(FileInfo fileInfo, String remark) {
        // 获取当前最大版本号
        Integer maxVersion = getMaxVersionNum(fileInfo.getId());
        int newVersionNum = (maxVersion == null ? 0 : maxVersion) + 1;

        FileVersion version = new FileVersion();
        version.setFileId(fileInfo.getId());
        version.setVersionNum(newVersionNum);
        version.setStorageId(fileInfo.getStorageId());
        version.setFileSize(fileInfo.getFileSize());
        version.setMd5(fileInfo.getMd5());
        version.setRemark(remark != null ? remark : "版本 " + newVersionNum);
        save(version);

        // 增加存储引用计数（历史版本也引用）
        if (fileInfo.getStorageId() != null) {
            fileStorageService.incrementRef(fileInfo.getStorageId());
        }
    }

    private Integer getMaxVersionNum(Long fileId) {
        FileVersion latest = getOne(new LambdaQueryWrapper<FileVersion>()
                .eq(FileVersion::getFileId, fileId)
                .orderByDesc(FileVersion::getVersionNum)
                .last("LIMIT 1"));
        return latest != null ? latest.getVersionNum() : null;
    }

    /**
     * 清理超出限制的旧版本
     */
    private void cleanupOldVersions(Long fileId) {
        List<FileVersion> versions = list(new LambdaQueryWrapper<FileVersion>()
                .eq(FileVersion::getFileId, fileId)
                .orderByDesc(FileVersion::getVersionNum));

        if (versions.size() > MAX_VERSIONS) {
            // 删除最旧的版本
            for (int i = MAX_VERSIONS; i < versions.size(); i++) {
                FileVersion old = versions.get(i);
                // 减少存储引用
                if (old.getStorageId() != null) {
                    fileStorageService.decrementRef(old.getStorageId());
                }
                removeById(old.getId());
                log.info("清理旧版本：fileId={}, versionNum={}", fileId, old.getVersionNum());
            }
        }
    }

    @Override
    public List<FileVersionVO> listVersions(Long fileId, Long userId) {
        FileInfo fileInfo = fileService.getById(fileId);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权限");
        }

        List<FileVersion> versions = list(new LambdaQueryWrapper<FileVersion>()
                .eq(FileVersion::getFileId, fileId)
                .orderByDesc(FileVersion::getVersionNum));

        List<FileVersionVO> result = new ArrayList<>();

        // 添加当前版本
        FileVersionVO current = new FileVersionVO();
        current.setId(fileInfo.getId());
        current.setVersionNum(getMaxVersionNum(fileId) != null ? getMaxVersionNum(fileId) + 1 : 1);
        current.setFileSize(fileInfo.getFileSize());
        current.setMd5(fileInfo.getMd5());
        current.setRemark("当前版本");
        current.setCreateTime(fileInfo.getUpdateTime());
        current.setIsCurrent(true);
        result.add(current);

        // 添加历史版本
        for (FileVersion v : versions) {
            FileVersionVO vo = new FileVersionVO();
            vo.setId(v.getId());
            vo.setVersionNum(v.getVersionNum());
            vo.setFileSize(v.getFileSize());
            vo.setMd5(v.getMd5());
            vo.setRemark(v.getRemark());
            vo.setCreateTime(v.getCreateTime());
            vo.setIsCurrent(false);
            result.add(vo);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo rollback(Long fileId, Integer versionNum, Long userId) {
        FileInfo fileInfo = fileService.getById(fileId);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId) || fileInfo.getDeleted() == 1) {
            throw new BusinessException("文件不存在或无权限");
        }

        // 查找目标版本
        FileVersion targetVersion = getOne(new LambdaQueryWrapper<FileVersion>()
                .eq(FileVersion::getFileId, fileId)
                .eq(FileVersion::getVersionNum, versionNum));
        if (targetVersion == null) {
            throw new BusinessException("版本不存在");
        }

        // 保存当前版本到历史
        saveCurrentAsVersion(fileInfo, "回滚前的版本");

        // 根据目标版本的 md5 查找对应的 storage（修复 storage_id 为 null 的情况）
        FileStorage targetStorage = fileStorageService.getOne(
                new LambdaQueryWrapper<FileStorage>()
                        .eq(FileStorage::getMd5, targetVersion.getMd5())
        );
        if (targetStorage == null) {
            throw new BusinessException("目标版本的存储文件不存在");
        }

        // 减少当前存储引用
        if (fileInfo.getStorageId() != null) {
            fileStorageService.decrementRef(fileInfo.getStorageId());
        }

        // 更新为目标版本
        fileInfo.setStorageId(targetStorage.getId());
        fileInfo.setFilePath(targetStorage.getStoragePath());
        fileInfo.setFileSize(targetVersion.getFileSize());
        fileInfo.setMd5(targetVersion.getMd5());
        fileStorageService.incrementRef(targetStorage.getId());
        fileService.updateById(fileInfo);

        log.info("用户{}回滚文件版本：fileId={}, versionNum={}", userId, fileId, versionNum);
        return fileInfo;
    }

    @Override
    public String getVersionDownloadUrl(Long fileId, Integer versionNum, Long userId) {
        FileInfo fileInfo = fileService.getById(fileId);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权限");
        }

        FileVersion version = getOne(new LambdaQueryWrapper<FileVersion>()
                .eq(FileVersion::getFileId, fileId)
                .eq(FileVersion::getVersionNum, versionNum));
        if (version == null) {
            throw new BusinessException("版本不存在");
        }

        FileStorage storage = fileStorageService.getById(version.getStorageId());
        if (storage == null) {
            throw new BusinessException("存储文件不存在");
        }

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucket())
                            .object(storage.getStoragePath())
                            .expiry(60, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            throw new BusinessException("获取下载链接失败");
        }
    }
}
