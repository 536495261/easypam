package com.neu.easypam.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neu.easypam.common.exception.BusinessException;
import com.neu.easypam.common.feign.StorageFeignClient;
import com.neu.easypam.file.config.MinioConfig;
import com.neu.easypam.file.entity.ChunkUpload;
import com.neu.easypam.file.entity.FileInfo;
import com.neu.easypam.file.mapper.ChunkUploadMapper;
import com.neu.easypam.file.service.ChunkUploadService;
import com.neu.easypam.file.service.FileService;
import com.neu.easypam.file.vo.ChunkInitVO;
import io.minio.*;
import io.minio.messages.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkUploadServiceImpl extends ServiceImpl<ChunkUploadMapper, ChunkUpload> implements ChunkUploadService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final StorageFeignClient storageFeignClient;
    private final FileService fileService;

    // 默认分片大小：5MB
    private static final int DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;


    @Override
    public ChunkInitVO initUpload(Long userId, Long parentId, String fileName, Long fileSize, String fileMd5, Integer chunkSize) {
        // 1. 校验存储空间
        storageFeignClient.validateSpace(userId, fileSize);

        // 2. 秒传检测
        FileInfo existingFile = fileService.quickUpload(fileMd5, fileName, userId, parentId);
        if (existingFile != null) {
            ChunkInitVO vo = new ChunkInitVO();
            vo.setQuickUpload(true);
            vo.setFileId(existingFile.getId());
            return vo;
        }

        // 3. 检查是否有未完成的上传任务（断点续传）
        ChunkUpload existingTask = getOne(new LambdaQueryWrapper<ChunkUpload>()
                .eq(ChunkUpload::getUserId, userId)
                .eq(ChunkUpload::getFileMd5, fileMd5)
                .eq(ChunkUpload::getStatus, 0));

        if (existingTask != null) {
            return buildChunkInitVO(existingTask);
        }

        // 4. 创建新的上传任务
        int actualChunkSize = chunkSize != null ? chunkSize : DEFAULT_CHUNK_SIZE;
        int chunkCount = (int) Math.ceil((double) fileSize / actualChunkSize);

        ChunkUpload task = new ChunkUpload();
        task.setUserId(userId);
        task.setParentId(parentId);
        task.setFileName(fileName);
        task.setFileSize(fileSize);
        task.setFileMd5(fileMd5);
        task.setChunkSize(actualChunkSize);
        task.setChunkCount(chunkCount);
        task.setUploadedChunks("");
        task.setStatus(0);
        save(task);

        ChunkInitVO vo = new ChunkInitVO();
        vo.setUploadId(task.getId());
        vo.setQuickUpload(false);
        vo.setUploadedChunks(new ArrayList<>());
        vo.setChunkCount(chunkCount);
        return vo;
    }

    @Override
    public void uploadChunk(Long uploadId, Integer chunkIndex, MultipartFile file, Long userId) {
        // 1. 获取上传任务
        ChunkUpload task = getById(uploadId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException("上传任务不存在或无权限");
        }
        if (task.getStatus() != 0) {
            throw new BusinessException("上传任务已完成或已取消");
        }

        try {
            // 2. 上传分片到MinIO
            String chunkPath = getChunkPath(task.getId(), chunkIndex);
            ensureBucketExists();
            
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(chunkPath)
                        .stream(inputStream, file.getSize(), -1)
                        .build());
            }

            // 3. 更新已上传分片记录
            updateUploadedChunks(task, chunkIndex);
            log.info("分片上传成功: uploadId={}, chunkIndex={}", uploadId, chunkIndex);

        } catch (Exception e) {
            log.error("分片上传失败", e);
            throw new BusinessException("分片上传失败: " + e.getMessage());
        }
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo mergeChunks(Long uploadId, Long userId) {
        // 1. 获取上传任务
        ChunkUpload task = getById(uploadId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException("上传任务不存在或无权限");
        }
        if (task.getStatus() != 0) {
            throw new BusinessException("上传任务已完成或已取消");
        }

        // 2. 检查所有分片是否已上传
        List<Integer> uploadedChunks = parseUploadedChunks(task.getUploadedChunks());
        if (uploadedChunks.size() != task.getChunkCount()) {
            throw new BusinessException("还有分片未上传完成，已上传: " + uploadedChunks.size() + "/" + task.getChunkCount());
        }

        try {
            // 3. 合并分片
            String finalPath = mergeChunksInMinio(task);

            // 4. 创建文件记录
            FileInfo fileInfo = createFileRecord(task, finalPath);

            // 5. 更新存储空间
            storageFeignClient.addUsedSpace(userId, task.getFileSize());

            // 6. 更新任务状态
            task.setStatus(1);
            updateById(task);

            // 7. 删除分片文件
            deleteChunks(task);

            log.info("分片合并成功: uploadId={}, fileName={}", uploadId, task.getFileName());
            return fileInfo;

        } catch (Exception e) {
            log.error("分片合并失败", e);
            throw new BusinessException("分片合并失败: " + e.getMessage());
        }
    }

    @Override
    public ChunkInitVO getUploadStatus(Long uploadId, Long userId) {
        ChunkUpload task = getById(uploadId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException("上传任务不存在或无权限");
        }
        return buildChunkInitVO(task);
    }

    private ChunkInitVO buildChunkInitVO(ChunkUpload task) {
        ChunkInitVO vo = new ChunkInitVO();
        vo.setUploadId(task.getId());
        vo.setQuickUpload(false);
        vo.setUploadedChunks(parseUploadedChunks(task.getUploadedChunks()));
        vo.setChunkCount(task.getChunkCount());
        return vo;
    }

    private List<Integer> parseUploadedChunks(String uploadedChunks) {
        if (uploadedChunks == null || uploadedChunks.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(uploadedChunks.split(","))
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .sorted()
                .collect(Collectors.toList());
    }

    private synchronized void updateUploadedChunks(ChunkUpload task, Integer chunkIndex) {
        List<Integer> chunks = parseUploadedChunks(task.getUploadedChunks());
        if (!chunks.contains(chunkIndex)) {
            chunks.add(chunkIndex);
            Collections.sort(chunks);
            task.setUploadedChunks(chunks.stream().map(String::valueOf).collect(Collectors.joining(",")));
            updateById(task);
        }
    }


    private String mergeChunksInMinio(ChunkUpload task) throws Exception {
        // 生成最终文件路径
        String extension = task.getFileName().contains(".") 
                ? task.getFileName().substring(task.getFileName().lastIndexOf(".")) 
                : "";
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String finalPath = datePath + "/" + UUID.randomUUID().toString().replace("-", "") + extension;

        // 收集所有分片
        List<ComposeSource> sources = new ArrayList<>();
        for (int i = 0; i < task.getChunkCount(); i++) {
            String chunkPath = getChunkPath(task.getId(), i);
            sources.add(ComposeSource.builder()
                    .bucket(minioConfig.getBucket())
                    .object(chunkPath)
                    .build());
        }

        // 合并分片
        minioClient.composeObject(ComposeObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(finalPath)
                .sources(sources)
                .build());

        return finalPath;
    }

    private void deleteChunks(ChunkUpload task) {
        for (int i = 0; i < task.getChunkCount(); i++) {
            try {
                String chunkPath = getChunkPath(task.getId(), i);
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(chunkPath)
                        .build());
            } catch (Exception e) {
                log.warn("删除分片失败: {}", e.getMessage());
            }
        }
    }

    private FileInfo createFileRecord(ChunkUpload task, String filePath) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setUserId(task.getUserId());
        fileInfo.setParentId(task.getParentId());
        fileInfo.setFileName(task.getFileName());
        fileInfo.setFilePath(filePath);
        fileInfo.setFileSize(task.getFileSize());
        fileInfo.setMd5(task.getFileMd5());
        fileInfo.setIsFolder(0);
        fileInfo.setFileType(getFileType(task.getFileName()));
        fileService.save(fileInfo);
        return fileInfo;
    }

    private String getFileType(String fileName) {
        if (fileName == null) return "other";
        String lower = fileName.toLowerCase();
        if (lower.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$")) return "image";
        if (lower.matches(".*\\.(mp4|avi|mkv|mov|wmv|flv)$")) return "video";
        if (lower.matches(".*\\.(mp3|wav|flac|aac|ogg)$")) return "audio";
        if (lower.matches(".*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|txt)$")) return "document";
        return "other";
    }

    private String getChunkPath(Long uploadId, Integer chunkIndex) {
        return "chunks/" + uploadId + "/" + chunkIndex;
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
        }
    }
}
