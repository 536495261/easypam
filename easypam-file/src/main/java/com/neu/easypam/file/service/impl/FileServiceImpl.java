package com.neu.easypam.file.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neu.easypam.common.dto.SaveShareDTO;
import com.neu.easypam.common.exception.BusinessException;
import com.neu.easypam.common.feign.StorageFeignClient;
import com.neu.easypam.file.config.MinioConfig;
import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.entity.FileStorage;
import com.neu.easypam.file.mapper.FileMapper;
import com.neu.easypam.file.mq.FileIndexProducer;
import com.neu.easypam.file.service.FileService;
import com.neu.easypam.file.service.FileStorageService;
import com.neu.easypam.file.service.FileCacheService;
import com.neu.easypam.file.service.ThumbnailService;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl extends ServiceImpl<FileMapper, FileInfo> implements FileService {
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final ThumbnailService thumbnailService;
    private final FileStorageService fileStorageService;
    private final FileCacheService fileCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo copy(Long fileId, Long targetParentId, Long userId) {
        // 1. 校验源文件存在且有权限
        FileInfo source = getById(fileId);
        if (source == null || !source.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权限");
        }

        // 2. 校验目标文件夹（0为根目录）
        if (targetParentId != 0) {
            FileInfo targetFolder = getById(targetParentId);
            if (targetFolder == null || !targetFolder.getUserId().equals(userId) || targetFolder.getIsFolder() != 1) {
                throw new BusinessException("目标文件夹不存在或无权限");
            }
        }

        // 3. 计算需要的存储空间
        long requiredSpace = calculateFolderSize(source, userId);
        storageFeignClient.validateSpace(userId, requiredSpace);

        // 4. 执行复制
        FileInfo copiedFile = copyFileOrFolder(source, targetParentId, userId);

        // 5. 更新存储空间
        storageFeignClient.addUsedSpace(userId, requiredSpace);

        // 6. 同步 ES 索引（递归发送创建消息）
        sendCreateIndexRecursive(copiedFile, userId);

        log.info("用户{}复制文件：{} -> parentId={}，占用空间：{}", userId, source.getFileName(), targetParentId, requiredSpace);
        return copiedFile;
    }

    /**
     * 计算文件/文件夹大小
     */
    private long calculateFolderSize(FileInfo file, Long userId) {
        if (file.getIsFolder() != 1) {
            return file.getFileSize();
        }

        // 文件夹：递归计算子内容大小
        List<FileInfo> children = list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getParentId, file.getId())
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getDeleted, 0));

        long totalSize = 0;
        for (FileInfo child : children) {
            totalSize += calculateFolderSize(child, userId);
        }
        return totalSize;
    }

    /**
     * 复制文件或文件夹（递归）
     */
    private FileInfo copyFileOrFolder(FileInfo source, Long targetParentId, Long userId) {
        // 生成唯一文件名
        String newFileName = generateUniqueFileName(source.getFileName(), targetParentId, userId);

        if (source.getIsFolder() == 1) {
            // 复制文件夹
            FileInfo newFolder = new FileInfo();
            newFolder.setUserId(userId);
            newFolder.setParentId(targetParentId);
            newFolder.setFileName(newFileName);
            newFolder.setIsFolder(1);
            newFolder.setFileSize(0L);
            newFolder.setFileType("folder");
            save(newFolder);

            // 递归复制子内容
            List<FileInfo> children = list(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getParentId, source.getId())
                    .eq(FileInfo::getUserId, userId)
                    .eq(FileInfo::getDeleted, 0));

            for (FileInfo child : children) {
                copyFileOrFolder(child, newFolder.getId(), userId);
            }

            return newFolder;
        } else {
            // 复制文件：创建新记录，复用存储路径，增加引用计数
            FileInfo newFile = new FileInfo();
            newFile.setUserId(userId);
            newFile.setParentId(targetParentId);
            newFile.setFileName(newFileName);
            newFile.setStorageId(source.getStorageId());
            newFile.setFilePath(source.getFilePath());  // 复用存储路径
            newFile.setFileSize(source.getFileSize());
            newFile.setContentType(source.getContentType());
            newFile.setMd5(source.getMd5());
            newFile.setIsFolder(0);
            newFile.setFileType(source.getFileType());
            save(newFile);
            
            // 增加存储引用计数
            if (source.getStorageId() != null) {
                fileStorageService.incrementRef(source.getStorageId());
                log.info("文件复制增加引用：storageId={}", source.getStorageId());
            }

            return newFile;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(Long fileId, Long targetParentId, Long userId) {
        // 1. 校验文件存在且有权限
        FileInfo fileInfo = getById(fileId);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权限");
        }
        
        // 2. 不能移动到原位置
        if (fileInfo.getParentId().equals(targetParentId)) {
            throw new BusinessException("文件已在目标文件夹中");
        }
        
        // 3. 校验目标文件夹（0为根目录，不需要校验）
        if (targetParentId != 0) {
            FileInfo targetFolder = getById(targetParentId);
            if (targetFolder == null || !targetFolder.getUserId().equals(userId) || targetFolder.getIsFolder() != 1) {
                throw new BusinessException("目标文件夹不存在或无权限");
            }
            
            // 4. 防止循环移动：不能把文件夹移动到自己的子目录下
            if (fileInfo.getIsFolder() == 1 && isDescendant(fileId, targetParentId, userId)) {
                throw new BusinessException("不能将文件夹移动到其子目录中");
            }
        }
        // 5. 处理同名文件
        String newFileName = generateUniqueFileName(fileInfo.getFileName(), targetParentId, userId);
        
        fileInfo.setFileName(newFileName);
        fileInfo.setParentId(targetParentId);
        updateById(fileInfo);
        
        // 清除缓存
        fileCacheService.evictFileInfo(fileId);
        
        // 同步 ES 索引（parentId 变更）
        fileIndexProducer.sendUpdateMessage(fileInfo);
        
        log.info("用户{}移动文件：{} -> parentId={}", userId, fileInfo.getFileName(), targetParentId);
    }

    /**
     * 检查 targetId 是否是 folderId 的子目录
     */
    private boolean isDescendant(Long folderId, Long targetId, Long userId) {
        Long currentId = targetId;
        while (currentId != null && currentId != 0) {
            if (currentId.equals(folderId)) {
                return true;
            }
            FileInfo parent = getById(currentId);
            if (parent == null || !parent.getUserId().equals(userId)) {
                break;
            }
            currentId = parent.getParentId();
        }
        return false;
    }
    /**
     * 生成唯一文件名，如有重名则添加序号
     */
    private String generateUniqueFileName(String fileName, Long parentId, Long userId) {
        // 查询目标文件夹下的所有文件名
        List<FileInfo> existingFiles = list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getParentId, parentId)
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getDeleted, 0)
                .select(FileInfo::getFileName));
        
        // 检查是否有同名文件
        boolean exists = existingFiles.stream()
                .anyMatch(f -> f.getFileName().equals(fileName));
        
        if (!exists) {
            return fileName;
        }
        
        // 分离文件名和扩展名
        String baseName;
        String extension;
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        } else {
            baseName = fileName;
            extension = "";
        }
        
        // 生成唯一名称
        int index = 1;
        String candidate = baseName + "(" + index + ")" + extension;
        Set<String> existingNames = existingFiles.stream()
                .map(FileInfo::getFileName)
                .collect(java.util.stream.Collectors.toSet());
        
        while (existingNames.contains(candidate)) {
            index++;
            candidate = baseName + "(" + index + ")" + extension;
        }
        
        return candidate;
    }

    private final StorageFeignClient storageFeignClient;
    private final FileIndexProducer fileIndexProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo upload(MultipartFile file, Long userId, Long parentId) {
        storageFeignClient.validateSpace(userId, file.getSize());
        try {
            String md5 = DigestUtil.md5Hex(file.getInputStream());
            String fileName = generateUniqueFileName(file.getOriginalFilename(), parentId, userId);
            
            // 使用去重存储服务（内容寻址）
            FileStorage storage = fileStorageService.store(file, md5);
            
            // 创建用户文件记录，关联存储
            FileInfo fileInfo = createFileRecordWithStorage(userId, parentId, fileName, storage);
            
            storageFeignClient.addUsedSpace(userId, file.getSize());
            fileIndexProducer.sendCreateMessage(fileInfo);
            
            log.info("用户{}上传文件成功：{}，storageId={}，refCount={}", 
                    userId, fileName, storage.getId(), storage.getRefCount());
            return fileInfo;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long fileId, Long userId) {
        // 删除改为移入回收站
        moveToTrash(fileId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(Long[] fileIds, Long userId) {
        // 批量删除改为批量移入回收站
        batchMoveToTrash(fileIds, userId);
    }

    @Override
    public void rename(Long fileId, String newName, Long userId) {
        FileInfo fileInfo = getById(fileId);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权限");
        }
        fileInfo.setFileName(newName);
        updateById(fileInfo);
        // 清除缓存
        fileCacheService.evictFileInfo(fileId);
        // 发送更新索引消息
        fileIndexProducer.sendUpdateMessage(fileInfo);
    }
    @Override
    public List<FileInfo> listFiles(Long userId, Long parentId) {
        return list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getParentId, parentId)
                .eq(FileInfo::getDeleted, 0)
                .orderByDesc(FileInfo::getIsFolder)  // 文件夹在前
                .orderByDesc(FileInfo::getCreateTime));
    }
    @Override
    public FileInfo createFolder(String folderName, Long userId, Long parentId) {
        // 检查同名文件夹
        long count = count(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getParentId, parentId)
                .eq(FileInfo::getFileName, folderName)
                .eq(FileInfo::getIsFolder, 1)
                .eq(FileInfo::getDeleted, 0));

        if (count > 0) {
            throw new BusinessException("文件夹已存在");
        }
        FileInfo folder = new FileInfo();
        folder.setUserId(userId);
        folder.setParentId(parentId);
        folder.setFileName(folderName);
        folder.setIsFolder(1);
        folder.setFileSize(0L);
        folder.setFileType("folder");
        save(folder);
        // 发送索引消息
        fileIndexProducer.sendCreateMessage(folder);
        return folder;
    }
    @Override
    public String getDownloadUrl(Long fileId, Long userId) {
        return getDownloadUrl(fileId, userId, 60); // 默认1小时
    }

    @Override
    public String getDownloadUrl(Long fileId, Long userId, Integer expireMinutes) {
        FileInfo fileInfo = getById(fileId);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权限");
        }

        if (fileInfo.getIsFolder() == 1) {
            throw new BusinessException("文件夹不支持下载");
        }

        // 限制最大过期时间为7天
        int minutes = Math.min(expireMinutes != null ? expireMinutes : 60, 7 * 24 * 60);

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucket())
                            .object(fileInfo.getFilePath())
                            .expiry(minutes, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            throw new BusinessException("获取下载链接失败");
        }
    }
    @Override
    public FileInfo quickUpload(String md5, String fileName, Long userId, Long parentId) {
        // 秒传：根据MD5查找已存在的存储
        FileStorage storage = fileStorageService.findByMd5(md5);
        if (storage == null) {
            return null; // 文件不存在，需要正常上传
        }
        
        // 校验存储空间
        storageFeignClient.validateSpace(userId, storage.getFileSize());

        // 增加存储引用计数
        fileStorageService.incrementRef(storage.getId());

        // 创建新的文件记录，关联存储
        FileInfo fileInfo = new FileInfo();
        fileInfo.setUserId(userId);
        fileInfo.setParentId(parentId);
        fileInfo.setFileName(generateUniqueFileName(fileName, parentId, userId));
        fileInfo.setStorageId(storage.getId());
        fileInfo.setFilePath(storage.getStoragePath());
        fileInfo.setFileSize(storage.getFileSize());
        fileInfo.setContentType(storage.getContentType());
        fileInfo.setMd5(storage.getMd5());
        fileInfo.setIsFolder(0);
        fileInfo.setFileType(getFileType(storage.getContentType()));
        save(fileInfo);

        // 更新已用空间
        storageFeignClient.addUsedSpace(userId, storage.getFileSize());
        
        // 发送索引消息
        fileIndexProducer.sendCreateMessage(fileInfo);

        log.info("用户{}秒传成功：{}，storageId={}，refCount+1", userId, fileName, storage.getId());
        return fileInfo;
    }

    @Override
    public void download(Long fileId, Long userId, HttpServletResponse response) throws IOException {
        FileInfo fileInfo = getById(fileId);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权限");
        }
        if (fileInfo.getIsFolder() == 1) {
            throw new BusinessException("文件夹不支持下载");
        }

        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(fileInfo.getFilePath())
                        .build())) {

            // 设置响应头
            response.setContentType(fileInfo.getContentType() != null ? fileInfo.getContentType() : "application/octet-stream");
            response.setContentLengthLong(fileInfo.getFileSize());
            response.setHeader("Content-Disposition", "attachment; filename=\"" +
                            URLEncoder.encode(fileInfo.getFileName(), StandardCharsets.UTF_8) + "\"");

            // 流式写入响应
            OutputStream outputStream = response.getOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();

            log.info("用户{}下载文件：{}", userId, fileInfo.getFileName());
        } catch (Exception e) {
            log.error("文件下载失败", e);
            throw new BusinessException("文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public void batchDownload(List<Long> fileIds, Long userId, HttpServletResponse response) {
        // 1. 设置响应头
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"files.zip\"");
        
        // 2. 创建 ZIP 输出流
        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (Long fileId : fileIds) {
                FileInfo file = getById(fileId);
                // 校验权限
                if (file == null || !file.getUserId().equals(userId)) {
                    continue; // 跳过无权限的文件
                }
                
                if (file.getIsFolder() == 1) {
                    // 递归添加文件夹内容
                    addFolderToZip(zos, file, userId, file.getFileName());
                } else {
                    // 添加单个文件
                    addFileToZip(zos, file, file.getFileName());
                }
            }
            zos.flush();
            log.info("用户{}批量下载{}个文件", userId, fileIds.size());
        } catch (Exception e) {
            log.error("批量下载失败", e);
            throw new BusinessException("文件压缩失败: " + e.getMessage());
        }
    }

    @Override
    public IPage<FileInfo> listFilesByPage(Long userId, Long parentId, int page, int size, String sortBy, String sortOrder) {
        Page<FileInfo> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<FileInfo> queryWrapper = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getParentId, parentId)
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getDeleted, 0);
        
        // 文件夹始终在前
        queryWrapper.orderByDesc(FileInfo::getIsFolder);
        
        // 动态排序
        if ("asc".equalsIgnoreCase(sortOrder)) {
            queryWrapper.orderByAsc(getSortColumn(sortBy));
        } else {
            queryWrapper.orderByDesc(getSortColumn(sortBy));
        }
        return page(pageParam, queryWrapper);
    }

    private SFunction<FileInfo, ?> getSortColumn(String sortBy) {
        if (sortBy == null) {
            return FileInfo::getCreateTime;
        }
        return switch (sortBy) {
            case "fileName" -> FileInfo::getFileName;
            case "fileSize" -> FileInfo::getFileSize;
            case "fileType" -> FileInfo::getFileType;
            case "updateTime" -> FileInfo::getUpdateTime;
            default -> FileInfo::getCreateTime;
        };
    }

    /**
     * 递归添加文件夹到ZIP
     */
    private void addFolderToZip(ZipOutputStream zos, FileInfo folder, Long userId, String basePath) {
        // 查询文件夹下的所有子文件/子文件夹
        List<FileInfo> children = list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getParentId, folder.getId())
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getDeleted, 0));

        // 如果文件夹为空，创建空目录
        if (children.isEmpty()) {
            try {
                zos.putNextEntry(new ZipEntry(basePath + "/"));
                zos.closeEntry();
            } catch (IOException e) {
                log.warn("创建空目录失败: {}", basePath);
            }
            return;
        }
        for (FileInfo child : children) {
            String childPath = basePath + "/" + child.getFileName();
            if (child.getIsFolder() == 1) {
                // 子文件夹 → 递归处理
                addFolderToZip(zos, child, userId, childPath);
            } else {
                // 文件 → 添加到ZIP
                addFileToZip(zos, child, childPath);
            }
        }
    }

    /**
     * 添加单个文件到ZIP
     */
    private void addFileToZip(ZipOutputStream zos, FileInfo file, String zipPath) {
        try {
            zos.putNextEntry(new ZipEntry(zipPath));
            try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(file.getFilePath())
                    .build())) {
                // 手动复制流
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    zos.write(buffer, 0, bytesRead);
                }
            }
            zos.closeEntry();
        } catch (Exception e) {
            log.error("添加文件到ZIP失败: {}", file.getFileName(), e);
            throw new BusinessException("文件压缩失败: " + e.getMessage());
        }
    }


    private FileInfo createFileRecord(Long userId,Long parentId,
                                      String filename,String filePath,Long filesize,String contentType,String md5){
        FileInfo fileInfo = new FileInfo();
        fileInfo.setUserId(userId);
        fileInfo.setParentId(parentId);
        fileInfo.setFileName(filename);
        fileInfo.setFilePath(filePath);
        fileInfo.setFileSize(filesize);
        fileInfo.setContentType(contentType);
        fileInfo.setMd5(md5);
        fileInfo.setIsFolder(0);
        fileInfo.setFileType(getFileType(contentType));
        save(fileInfo);
        return fileInfo;
    }

    /**
     * 创建文件记录（关联去重存储）
     */
    private FileInfo createFileRecordWithStorage(Long userId, Long parentId, String fileName, FileStorage storage) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setUserId(userId);
        fileInfo.setParentId(parentId);
        fileInfo.setFileName(fileName);
        fileInfo.setStorageId(storage.getId());
        fileInfo.setFilePath(storage.getStoragePath());
        fileInfo.setFileSize(storage.getFileSize());
        fileInfo.setContentType(storage.getContentType());
        fileInfo.setMd5(storage.getMd5());
        fileInfo.setIsFolder(0);
        fileInfo.setFileType(getFileType(storage.getContentType()));
        save(fileInfo);
        return fileInfo;
    }

    /**
     * 根据contentType判断文件类型
     */
    private String getFileType(String contentType) {
        if (contentType == null) return "other";
        if (contentType.startsWith("image/")) return "image";
        if (contentType.startsWith("video/")) return "video";
        if (contentType.startsWith("audio/")) return "audio";
        if (contentType.contains("pdf") || contentType.contains("word") ||
            contentType.contains("excel") || contentType.contains("text")) return "document";
        return "other";
    }
    private String uploadToMinio(MultipartFile file) throws Exception {
        ensureBucketExists();
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".") ?
                originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String dataPath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectName = dataPath + "/" + UUID.randomUUID().toString().replace("-", "") + extension;
        try(InputStream inputStream = file.getInputStream()){
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .stream(inputStream,file.getSize(),-1)
                    .contentType(file.getContentType())
                    .build());
        }
        return objectName;
    }
    private void ensureBucketExists() throws Exception{
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
        if(!exists){
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
        }
    }

    // ========== 回收站功能 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveToTrash(Long fileId, Long userId) {
        FileInfo file = getById(fileId);
        if (file == null || !file.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权限");
        }
        if (file.getDeleted() == 1) {
            throw new BusinessException("文件已在回收站中");
        }

        if (file.getIsFolder() == 1) {
            // 文件夹：递归移入回收站
            moveToTrashRecursive(fileId, userId);
        }

        file.setDeleted(1);
        file.setDeleteTime(LocalDateTime.now());
        updateById(file);

        // 清除缓存
        fileCacheService.evictFileInfo(fileId);

        // 从 ES 索引中删除（回收站文件不应被搜索到）
        sendDeleteIndexRecursive(file, userId);

        log.info("用户{}将文件移入回收站：{}", userId, file.getFileName());
    }

    /**
     * 递归将文件夹内容移入回收站
     */
    private void moveToTrashRecursive(Long folderId, Long userId) {
        List<FileInfo> children = list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getParentId, folderId)
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getDeleted, 0));

        for (FileInfo child : children) {
            if (child.getIsFolder() == 1) {
                moveToTrashRecursive(child.getId(), userId);
            }
            child.setDeleted(1);
            child.setDeleteTime(LocalDateTime.now());
            updateById(child);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchMoveToTrash(Long[] fileIds, Long userId) {
        if (fileIds == null || fileIds.length == 0) {
            return;
        }
        for (Long fileId : fileIds) {
            try {
                moveToTrash(fileId, userId);
            } catch (BusinessException e) {
                // 跳过无权限或已删除的文件
                log.warn("跳过文件{}：{}", fileId, e.getMessage());
            }
        }
    }

    @Override
    public List<FileInfo> listTrash(Long userId) {
        // 只返回顶层被删除的文件（parentId对应的父文件夹未被删除，或者是根目录下的文件）
        return list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getDeleted, 1)
                .orderByDesc(FileInfo::getDeleteTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long fileId, Long userId) {
        FileInfo file = getById(fileId);
        if (file == null || !file.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权限");
        }
        if (file.getDeleted() != 1) {
            throw new BusinessException("文件不在回收站中");
        }

        // 检查原父目录是否存在
        Long targetParentId = file.getParentId();
        if (targetParentId != 0) {
            FileInfo parent = getById(targetParentId);
            if (parent == null || parent.getDeleted() == 1) {
                // 父目录不存在或也在回收站，恢复到根目录
                targetParentId = 0L;
            }
        }

        // 处理同名文件
        String newFileName = generateUniqueFileName(file.getFileName(), targetParentId, userId);

        file.setDeleted(0);
        file.setDeleteTime(null);
        file.setParentId(targetParentId);
        file.setFileName(newFileName);
        updateById(file);

        // 清除缓存（恢复后数据变更）
        fileCacheService.evictFileInfo(fileId);

        // 如果是文件夹，递归恢复子内容
        if (file.getIsFolder() == 1) {
            restoreRecursive(fileId, userId);
        }

        // 重新添加到 ES 索引
        sendCreateIndexRecursive(file, userId);

        log.info("用户{}从回收站恢复文件：{}", userId, file.getFileName());
    }

    /**
     * 递归恢复文件夹内容
     */
    private void restoreRecursive(Long folderId, Long userId) {
        List<FileInfo> children = list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getParentId, folderId)
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getDeleted, 1));

        for (FileInfo child : children) {
            child.setDeleted(0);
            child.setDeleteTime(null);
            updateById(child);

            if (child.getIsFolder() == 1) {
                restoreRecursive(child.getId(), userId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePermanently(Long fileId, Long userId) {
        FileInfo file = getById(fileId);
        if (file == null || !file.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权限");
        }

        long freedSpace = 0;
        if (file.getIsFolder() == 1) {
            // 文件夹：递归彻底删除
            freedSpace = deletePermanentlyRecursive(fileId, userId);
        } else {
            freedSpace = file.getFileSize();
            // 减少存储引用计数（可能触发物理删除）
            if (file.getStorageId() != null) {
                boolean physicalDeleted = fileStorageService.decrementRef(file.getStorageId());
                if (physicalDeleted) {
                    log.info("文件物理删除：storageId={}", file.getStorageId());
                }
            }
        }

        removeById(fileId);
        // 发送删除索引消息
        fileIndexProducer.sendDeleteMessage(fileId);

        // 释放存储空间
        if (freedSpace > 0) {
            storageFeignClient.reduceUsedSpace(userId, freedSpace);
        }

        log.info("用户{}彻底删除文件：{}，释放空间：{}", userId, file.getFileName(), freedSpace);
    }

    /**
     * 递归彻底删除文件夹内容
     */
    private long deletePermanentlyRecursive(Long folderId, Long userId) {
        List<FileInfo> children = list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getParentId, folderId)
                .eq(FileInfo::getUserId, userId));

        long freedSpace = 0;
        for (FileInfo child : children) {
            if (child.getIsFolder() == 1) {
                freedSpace += deletePermanentlyRecursive(child.getId(), userId);
            } else {
                freedSpace += child.getFileSize();
                // 减少存储引用计数（可能触发物理删除）
                if (child.getStorageId() != null) {
                    boolean physicalDeleted = fileStorageService.decrementRef(child.getStorageId());
                    if (physicalDeleted) {
                        log.info("文件物理删除：storageId={}", child.getStorageId());
                    }
                }
            }
            removeById(child.getId());
            // 发送删除索引消息
            fileIndexProducer.sendDeleteMessage(child.getId());
        }
        return freedSpace;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void emptyTrash(Long userId) {
        // 查询回收站中的所有文件
        List<FileInfo> trashFiles = list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getDeleted, 1));

        long totalFreedSpace = 0;
        for (FileInfo file : trashFiles) {
            if (file.getIsFolder() != 1) {
                totalFreedSpace += file.getFileSize();
                // 减少存储引用计数（可能触发物理删除）
                if (file.getStorageId() != null) {
                    boolean physicalDeleted = fileStorageService.decrementRef(file.getStorageId());
                    if (physicalDeleted) {
                        log.info("文件物理删除：storageId={}", file.getStorageId());
                    }
                }
            }
            removeById(file.getId());
            // 发送删除索引消息
            fileIndexProducer.sendDeleteMessage(file.getId());
        }

        // 释放存储空间
        if (totalFreedSpace > 0) {
            storageFeignClient.reduceUsedSpace(userId, totalFreedSpace);
        }

        log.info("用户{}清空回收站，释放空间：{}", userId, totalFreedSpace);
    }

    @Override
    public void downloadByShared(Long fileId, HttpServletResponse response) {
        FileInfo fileInfo = getById(fileId);
        if(fileInfo == null || fileInfo.getDeleted() == 1){
            throw new BusinessException("文件已被分享人删除");
        }
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(fileInfo.getFilePath())
                        .build())){
            response.setContentType(fileInfo.getContentType() != null ? fileInfo.getContentType() : "application/octet-stream");
            response.setContentLengthLong(fileInfo.getFileSize());
            response.setHeader("Content-Disposition", "attachment; filename=\"" +
                    URLEncoder.encode(fileInfo.getFileName(), StandardCharsets.UTF_8)+"\"");
            OutputStream outputStream = response.getOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            log.info("分享文件{}已经下载完成",fileInfo.getFileName());
        }catch(Exception e){
            log.error("文件下载失败", e);
            throw new BusinessException("文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public String getInternalDownloadUrl(Long fileId, Integer expireMinutes) {
        FileInfo fileInfo = getById(fileId);
        if (fileInfo == null || fileInfo.getDeleted() == 1) {
            throw new BusinessException("文件不存在或已删除");
        }
        if (fileInfo.getIsFolder() == 1) {
            throw new BusinessException("文件夹不支持下载");
        }

        int minutes = expireMinutes != null ? expireMinutes : 60;

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucket())
                            .object(fileInfo.getFilePath())
                            .expiry(minutes, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("获取下载链接失败", e);
            throw new BusinessException("获取下载链接失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo saveShared(SaveShareDTO saveShareDTO) {
        FileInfo sourceFile = getById(saveShareDTO.getSourceFileId());
        if (sourceFile == null || sourceFile.getDeleted() == 1) {
            throw new BusinessException("源文件已被删除");
        }

        Long targetUserId = saveShareDTO.getTargetUserId();
        Long targetParentId = saveShareDTO.getParentId() != null ? saveShareDTO.getParentId() : 0L;

        // 校验存储空间
        long totalSize = calculateTotalSize(sourceFile);
        storageFeignClient.validateSpace(targetUserId, totalSize);

        // 处理同名
        String newFileName = generateUniqueFileName(sourceFile.getFileName(), targetParentId, targetUserId);

        FileInfo newFile;
        if (sourceFile.getIsFolder() == 0) {
            // 文件：复制记录并增加引用计数
            newFile = new FileInfo();
            newFile.setUserId(targetUserId);
            newFile.setParentId(targetParentId);
            newFile.setFileName(newFileName);
            newFile.setStorageId(sourceFile.getStorageId());
            newFile.setFilePath(sourceFile.getFilePath());
            newFile.setFileSize(sourceFile.getFileSize());
            newFile.setContentType(sourceFile.getContentType());
            newFile.setMd5(sourceFile.getMd5());
            newFile.setIsFolder(0);
            newFile.setFileType(sourceFile.getFileType());
            save(newFile);
            
            // 增加存储引用计数
            if (sourceFile.getStorageId() != null) {
                fileStorageService.incrementRef(sourceFile.getStorageId());
                log.info("保存分享增加引用：storageId={}", sourceFile.getStorageId());
            }
        } else {
            // 文件夹：创建文件夹并递归复制内容
            newFile = new FileInfo();
            newFile.setUserId(targetUserId);
            newFile.setParentId(targetParentId);
            newFile.setFileName(newFileName);
            newFile.setIsFolder(1);
            newFile.setFileType("folder");
            newFile.setFileSize(0L);
            newFile.setDeleted(0);
            save(newFile);
            // 递归复制子文件
            saveSharedDir(sourceFile.getId(), newFile.getId(), targetUserId);
        }

        // 更新已用空间
        storageFeignClient.addUsedSpace(targetUserId, totalSize);

        // 同步 ES 索引
        sendCreateIndexRecursive(newFile, targetUserId);

        log.info("用户{}保存分享文件到网盘：{}", targetUserId, newFileName);
        return newFile;
    }

    /**
     * 递归复制分享文件夹内容
     */
    private void saveSharedDir(Long sourceParentId, Long targetParentId, Long targetUserId) {
        List<FileInfo> children = list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getParentId, sourceParentId)
                .eq(FileInfo::getDeleted, 0));

        for (FileInfo child : children) {
            if (child.getIsFolder() == 1) {
                // 子文件夹：创建并递归
                FileInfo newFolder = new FileInfo();
                newFolder.setUserId(targetUserId);
                newFolder.setParentId(targetParentId);
                newFolder.setFileName(child.getFileName());
                newFolder.setIsFolder(1);
                newFolder.setFileType("folder");
                newFolder.setFileSize(0L);
                newFolder.setDeleted(0);
                save(newFolder);
                saveSharedDir(child.getId(), newFolder.getId(), targetUserId);
            } else {
                // 文件：复制记录并增加引用计数
                FileInfo newFile = new FileInfo();
                newFile.setUserId(targetUserId);
                newFile.setParentId(targetParentId);
                newFile.setFileName(child.getFileName());
                newFile.setStorageId(child.getStorageId());
                newFile.setFilePath(child.getFilePath());
                newFile.setFileSize(child.getFileSize());
                newFile.setContentType(child.getContentType());
                newFile.setMd5(child.getMd5());
                newFile.setIsFolder(0);
                newFile.setFileType(child.getFileType());
                save(newFile);
                
                // 增加存储引用计数
                if (child.getStorageId() != null) {
                    fileStorageService.incrementRef(child.getStorageId());
                }
            }
        }
    }
    /**
     * 计算文件/文件夹总大小
     */
    private long calculateTotalSize(FileInfo file) {
        if (file.getIsFolder() == 0) {
            return file.getFileSize();
        }
        // 文件夹：递归计算
        long total = 0;
        List<FileInfo> children = list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getParentId, file.getId())
                .eq(FileInfo::getDeleted, 0));
        for (FileInfo child : children) {
            total += calculateTotalSize(child);
        }
        return total;
    }

    @Override
    public List<FileInfo> listFolderChildren(Long folderId) {
        return list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getParentId, folderId)
                .eq(FileInfo::getDeleted, 0)
                .orderByDesc(FileInfo::getIsFolder)
                .orderByDesc(FileInfo::getCreateTime));
    }

    @Override
    public void downloadFolderAsZip(Long folderId, HttpServletResponse response) {
        FileInfo folder = getById(folderId);
        if (folder == null || folder.getDeleted() == 1) {
            throw new BusinessException("文件夹不存在或已删除");
        }
        if (folder.getIsFolder() != 1) {
            throw new BusinessException("不是文件夹");
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + 
                URLEncoder.encode(folder.getFileName(), StandardCharsets.UTF_8) + ".zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            addFolderToZipInternal(zos, folder, folder.getFileName());
            zos.flush();
            log.info("分享文件夹{}下载完成", folder.getFileName());
        } catch (Exception e) {
            log.error("文件夹下载失败", e);
            throw new BusinessException("文件夹下载失败: " + e.getMessage());
        }
    }

    /**
     * 递归添加文件夹到ZIP（内部接口用，不校验用户权限）
     */
    private void addFolderToZipInternal(ZipOutputStream zos, FileInfo folder, String basePath) {
        List<FileInfo> children = list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getParentId, folder.getId())
                .eq(FileInfo::getDeleted, 0));

        if (children.isEmpty()) {
            try {
                zos.putNextEntry(new ZipEntry(basePath + "/"));
                zos.closeEntry();
            } catch (IOException e) {
                log.warn("创建空目录失败: {}", basePath);
            }
            return;
        }

        for (FileInfo child : children) {
            String childPath = basePath + "/" + child.getFileName();
            if (child.getIsFolder() == 1) {
                addFolderToZipInternal(zos, child, childPath);
            } else {
                addFileToZip(zos, child, childPath);
            }
        }
    }

    // ========== ES 索引同步辅助方法 ==========

    /**
     * 递归发送创建索引消息（用于复制、恢复、保存分享）
     */
    private void sendCreateIndexRecursive(FileInfo file, Long userId) {
        fileIndexProducer.sendCreateMessage(file);
        
        if (file.getIsFolder() == 1) {
            List<FileInfo> children = list(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getParentId, file.getId())
                    .eq(FileInfo::getUserId, userId)
                    .eq(FileInfo::getDeleted, 0));
            for (FileInfo child : children) {
                sendCreateIndexRecursive(child, userId);
            }
        }
    }

    /**
     * 递归发送删除索引消息（用于移入回收站）
     */
    private void sendDeleteIndexRecursive(FileInfo file, Long userId) {
        fileIndexProducer.sendDeleteMessage(file.getId());
        
        if (file.getIsFolder() == 1) {
            List<FileInfo> children = list(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getParentId, file.getId())
                    .eq(FileInfo::getUserId, userId));
            for (FileInfo child : children) {
                sendDeleteIndexRecursive(child, userId);
            }
        }
    }

    // ========== 缩略图功能 ==========

    @Override
    public String getThumbnailUrl(Long fileId, Long userId) {
        FileInfo file = getById(fileId);
        if (file == null || !file.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权限");
        }
        return getThumbnailUrlInternal(file);
    }

    @Override
    public String getInternalThumbnailUrl(Long fileId) {
        FileInfo file = getById(fileId);
        if (file == null || file.getDeleted() == 1) {
            return null;
        }
        return getThumbnailUrlInternal(file);
    }

    private String getThumbnailUrlInternal(FileInfo file) {
        if (file.getIsFolder() == 1) {
            return null;
        }
        
        // 如果已有缩略图，直接返回URL
        if (file.getThumbnailPath() != null && !file.getThumbnailPath().isEmpty()) {
            return thumbnailService.getThumbnailUrl(file.getThumbnailPath(), 60);
        }
        
        // 如果支持生成缩略图但还没生成，异步生成
        if (thumbnailService.supportsThumbnail(file.getContentType())) {
            // 同步生成（首次请求时）
            String thumbnailPath = thumbnailService.generateThumbnail(file.getFilePath(), file.getContentType());
            if (thumbnailPath != null) {
                file.setThumbnailPath(thumbnailPath);
                updateById(file);
                return thumbnailService.getThumbnailUrl(thumbnailPath, 60);
            }
        }
        
        return null;
    }
}
