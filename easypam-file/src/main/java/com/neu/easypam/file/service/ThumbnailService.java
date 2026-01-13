package com.neu.easypam.file.service;

import java.io.InputStream;

/**
 * 缩略图服务
 */
public interface ThumbnailService {
    
    /**
     * 生成并上传缩略图
     * @param originalPath 原图在MinIO中的路径
     * @param contentType 文件MIME类型
     * @return 缩略图路径，如果不支持生成则返回null
     */
    String generateThumbnail(String originalPath, String contentType);
    
    /**
     * 获取缩略图URL
     * @param thumbnailPath 缩略图路径
     * @param expireMinutes 过期时间（分钟）
     * @return 预签名URL
     */
    String getThumbnailUrl(String thumbnailPath, int expireMinutes);
    
    /**
     * 判断是否支持生成缩略图
     */
    boolean supportsThumbnail(String contentType);
}
