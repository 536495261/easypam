package com.neu.easypam.file.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neu.easypam.common.exception.BusinessException;
import com.neu.easypam.common.feign.StorageFeignClient;
import com.neu.easypam.file.config.MinioConfig;
import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.mapper.FileMapper;
import com.neu.easypam.file.service.FileService;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
            // 复制文件：创建新记录，复用存储路径
            FileInfo newFile = new FileInfo();
            newFile.setUserId(userId);
            newFile.setParentId(targetParentId);
            newFile.setFileName(newFileName);
            newFile.setFilePath(source.getFilePath());  // 复用存储路径
            newFile.setFileSize(source.getFileSize());
            newFile.setContentType(source.getContentType());
            newFile.setMd5(source.getMd5());
            newFile.setIsFolder(0);
            newFile.setFileType(source.getFileType());
            save(newFile);

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

    private final StorageFeignClient  storageFeignClient;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo upload(MultipartFile file, Long userId, Long parentId) {
        storageFeignClient.validateSpace(userId,file.getSize());
        try{
            String md5 = DigestUtil.md5Hex(file.getInputStream());
            FileInfo existingFile = getOne(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getMd5,md5)
                    .eq(FileInfo::getDeleted,0)
                    .last("limit 1"));
            if(existingFile!=null){
                // 秒传：复用已有文件的存储路径，但使用当前用户的userId和parentId
                FileInfo fileInfo = createFileRecord(userId, parentId,
                        file.getOriginalFilename(), existingFile.getFilePath(),
                        file.getSize(), file.getContentType(), md5);
                storageFeignClient.addUsedSpace(userId, file.getSize());
                log.info("用户{}秒传成功：{}", userId, file.getOriginalFilename());
                return fileInfo;
            }
            String filePath = uploadToMinio(file);
            FileInfo fileInfo = createFileRecord(userId,parentId,file.getOriginalFilename()
                    ,filePath,file.getSize(),file.getContentType(),md5);
            storageFeignClient.addUsedSpace(userId,file.getSize());
            log.info("用户{}上传文件成功：{}", userId, file.getOriginalFilename());
            return fileInfo;
        }catch(Exception e){
            log.error("文件上传失败",e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long fileId, Long userId) {
        FileInfo fileInfo = getById(fileId);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权限");
        }
        if (fileInfo.getIsFolder() == 1) {
            // 文件夹：递归删除子文件，累计释放空间
            long freedSpace = deleteFolderRecursive(fileId, userId);
            // 删除文件夹本身
            removeById(fileId);
            // 一次性更新存储空间
            if (freedSpace > 0) {
                storageFeignClient.reduceUsedSpace(userId, freedSpace);
            }
            log.info("用户{}删除文件夹：{}，释放空间：{}", userId, fileInfo.getFileName(), freedSpace);
        } else {
            // 文件：直接删除
            removeById(fileId);
            storageFeignClient.reduceUsedSpace(userId, fileInfo.getFileSize());
            log.info("用户{}删除文件：{}", userId, fileInfo.getFileName());
        }
    }
    /**
     * 递归删除文件夹内容，返回释放的空间大小
     */
    private long deleteFolderRecursive(Long folderId, Long userId) {
        List<FileInfo> children = list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getParentId, folderId)
                .eq(FileInfo::getDeleted, 0));

        if (children == null || children.isEmpty()) {
            return 0;
        }

        long freedSpace = 0;
        for (FileInfo child : children) {
            if (child.getIsFolder() == 1) {
                // 子文件夹：递归删除
                freedSpace += deleteFolderRecursive(child.getId(), userId);
                removeById(child.getId());
            } else {
                // 文件：累计大小并删除
                freedSpace += child.getFileSize();
                removeById(child.getId());
            }
        }
        return freedSpace;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(Long[] fileIds, Long userId) {
        if (fileIds == null || fileIds.length == 0) {
            return;
        }

        long totalFreedSpace = 0;
        int deletedCount = 0;

        for (Long fileId : fileIds) {
            FileInfo file = getById(fileId);
            // 跳过不存在或无权限的文件
            if (file == null || !file.getUserId().equals(userId)) {
                continue;
            }

            if (file.getIsFolder() == 1) {
                // 文件夹：递归删除并累计释放空间
                totalFreedSpace += deleteFolderRecursive(fileId, userId);
                removeById(fileId);
            } else {
                // 文件：累计大小并删除
                totalFreedSpace += file.getFileSize();
                removeById(fileId);
            }
            deletedCount++;
        }
        // 一次性更新存储空间
        if (totalFreedSpace > 0) {
            storageFeignClient.reduceUsedSpace(userId, totalFreedSpace);
        }

        log.info("用户{}批量删除{}个文件，释放空间：{}", userId, deletedCount, totalFreedSpace);
    }

    @Override
    public void rename(Long fileId, String newName, Long userId) {
        FileInfo fileInfo = getById(fileId);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权限");
        }
        fileInfo.setFileName(newName);
        updateById(fileInfo);
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
        // 秒传：根据MD5查找已存在的文件
        FileInfo existingFile = getOne(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getMd5, md5)
                .eq(FileInfo::getDeleted, 0)
                .last("LIMIT 1"));
        if (existingFile == null) {
            return null; // 文件不存在，需要正常上传
        }
        // 校验存储空间
        storageFeignClient.validateSpace(userId, existingFile.getFileSize());

        // 创建新的文件记录，复用存储路径
        FileInfo fileInfo = createFileRecord(userId, parentId, fileName,
                existingFile.getFilePath(), existingFile.getFileSize(),
                existingFile.getContentType(), md5);

        // 更新已用空间
        storageFeignClient.addUsedSpace(userId, existingFile.getFileSize());

        log.info("用户{}秒传成功：{}", userId, fileName);
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
}
