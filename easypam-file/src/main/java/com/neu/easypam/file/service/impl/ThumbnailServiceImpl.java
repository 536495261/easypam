package com.neu.easypam.file.service.impl;

import com.neu.easypam.file.config.MinioConfig;
import com.neu.easypam.file.service.ThumbnailService;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 缩略图服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailServiceImpl implements ThumbnailService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    // 支持生成缩略图的图片类型
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/bmp", "image/webp"
    );

    // 缩略图配置
    private static final int THUMBNAIL_WIDTH = 200;
    private static final int THUMBNAIL_HEIGHT = 200;
    private static final double THUMBNAIL_QUALITY = 0.8;
    private static final String THUMBNAIL_PREFIX = "thumbnails/";

    @Override
    public String generateThumbnail(String originalPath, String contentType) {
        if (!supportsThumbnail(contentType)) {
            return null;
        }

        try {
            // 1. 从MinIO获取原图
            InputStream originalStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(originalPath)
                            .build());

            // 2. 生成缩略图
            ByteArrayOutputStream thumbnailOutput = new ByteArrayOutputStream();
            Thumbnails.of(originalStream)
                    .size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                    .keepAspectRatio(true)
                    .outputQuality(THUMBNAIL_QUALITY)
                    .outputFormat("jpg")
                    .toOutputStream(thumbnailOutput);

            originalStream.close();

            // 3. 上传缩略图到MinIO
            String thumbnailPath = THUMBNAIL_PREFIX + originalPath.replace("/", "_") + "_thumb.jpg";
            byte[] thumbnailBytes = thumbnailOutput.toByteArray();

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(thumbnailPath)
                            .stream(new ByteArrayInputStream(thumbnailBytes), thumbnailBytes.length, -1)
                            .contentType("image/jpeg")
                            .build());

            log.debug("缩略图生成成功：{} -> {}", originalPath, thumbnailPath);
            return thumbnailPath;

        } catch (Exception e) {
            log.error("生成缩略图失败：{}", originalPath, e);
            return null;
        }
    }

    @Override
    public String getThumbnailUrl(String thumbnailPath, int expireMinutes) {
        if (thumbnailPath == null || thumbnailPath.isEmpty()) {
            return null;
        }

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucket())
                            .object(thumbnailPath)
                            .expiry(expireMinutes, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("获取缩略图URL失败：{}", thumbnailPath, e);
            return null;
        }
    }

    @Override
    public boolean supportsThumbnail(String contentType) {
        return contentType != null && SUPPORTED_IMAGE_TYPES.contains(contentType.toLowerCase());
    }
}
