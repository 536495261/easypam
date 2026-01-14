package com.neu.easypam.file.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neu.easypam.common.exception.BusinessException;
import com.neu.easypam.file.config.MinioConfig;
import com.neu.easypam.file.entity.FileStorage;
import com.neu.easypam.file.mapper.FileStorageMapper;
import com.neu.easypam.file.service.FileStorageService;
import com.neu.easypam.file.vo.StorageStatsVO;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl extends ServiceImpl<FileStorageMapper, FileStorage>
        implements FileStorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileStorage store(MultipartFile file, String md5) {
        // 1. 先检查是否已存在（快速路径）
        FileStorage existing = findByMd5(md5);
        if (existing != null) {
            baseMapper.incrementRefCount(existing.getId());
            log.info("文件去重命中：md5={}，refCount={}", md5, existing.getRefCount() + 1);
            return existing;
        }

        // 2. 不存在则上传到 MinIO
        String storagePath = uploadToMinio(file);

        // 3. 尝试插入数据库（依赖 md5 唯一约束处理并发）
        FileStorage storage = new FileStorage();
        storage.setMd5(md5);
        storage.setStoragePath(storagePath);
        storage.setFileSize(file.getSize());
        storage.setContentType(file.getContentType());
        storage.setRefCount(1);

        try {
            save(storage);
            log.info("新文件存储：md5={}，path={}", md5, storagePath);
            return storage;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发插入冲突：另一个线程已经插入了相同 md5 的记录
            // 删除刚上传的重复文件
            deleteFromMinio(storagePath);
            // 增加已存在记录的引用计数
            existing = findByMd5(md5);
            if (existing != null) {
                baseMapper.incrementRefCount(existing.getId());
                log.info("并发去重：md5={}，删除重复文件，refCount+1", md5);
                return existing;
            }
            throw new BusinessException("文件存储失败");
        }
    }

    @Override
    public FileStorage findByMd5(String md5) {
        return getOne(new LambdaQueryWrapper<FileStorage>()
                .eq(FileStorage::getMd5, md5)
                .last("LIMIT 1"));
    }

    @Override
    public void incrementRef(Long storageId) {
        int rows = baseMapper.incrementRefCount(storageId);
        if (rows == 0) {
            throw new BusinessException("存储记录不存在：" + storageId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean decrementRef(Long storageId) {
        FileStorage storage = getById(storageId);
        if (storage == null) {
            return false;
        }

        int rows = baseMapper.decrementRefCount(storageId);
        if (rows == 0) {
            return false;
        }

        storage = getById(storageId);
        if (storage != null && storage.getRefCount() == 0) {
            deleteFromMinio(storage.getStoragePath());
            removeById(storageId);
            log.info("文件物理删除：md5={}，path={}", storage.getMd5(), storage.getStoragePath());
            return true;
        }

        log.info("引用计数减少：storageId={}，剩余refCount={}",
                storageId, storage != null ? storage.getRefCount() : "已删除");
        return false;
    }

    @Override
    public StorageStatsVO getStorageStats() {
        StorageStatsVO stats = new StorageStatsVO();
        List<FileStorage> storages = list();

        if (storages.isEmpty()) {
            stats.setPhysicalFileCount(0L);
            stats.setLogicalFileCount(0L);
            stats.setPhysicalStorageSize(0L);
            stats.setLogicalStorageSize(0L);
            stats.setSavedStorageSize(0L);
            stats.setDeduplicationRate(0.0);
            stats.setAvgRefCount(0.0);
            return stats;
        }

        long physicalCount = storages.size();
        long logicalCount = 0;
        long physicalSize = 0;
        long logicalSize = 0;

        for (FileStorage storage : storages) {
            logicalCount += storage.getRefCount();
            physicalSize += storage.getFileSize();
            logicalSize += storage.getFileSize() * storage.getRefCount();
        }

        stats.setPhysicalFileCount(physicalCount);
        stats.setLogicalFileCount(logicalCount);
        stats.setPhysicalStorageSize(physicalSize);
        stats.setLogicalStorageSize(logicalSize);
        stats.setSavedStorageSize(logicalSize - physicalSize);
        stats.setDeduplicationRate(logicalSize > 0 ?
                (double) (logicalSize - physicalSize) / logicalSize * 100 : 0.0);
        stats.setAvgRefCount(physicalCount > 0 ? (double) logicalCount / physicalCount : 0.0);

        return stats;
    }

    private String uploadToMinio(MultipartFile file) {
        try {
            ensureBucketExists();
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
            String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String objectName = datePath + "/" + UUID.randomUUID().toString().replace("-", "") + extension;

            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(objectName)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }
            return objectName;
        } catch (Exception e) {
            throw new BusinessException("文件上传失败：" + e.getMessage());
        }
    }

    private void deleteFromMinio(String storagePath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(storagePath)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 文件删除失败：{}", storagePath, e);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
        }
    }
}
