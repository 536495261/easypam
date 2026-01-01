package com.neu.easypam.user.util;

import cn.hutool.core.util.IdUtil;
import com.neu.easypam.user.config.MinioConfig;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioUtil {
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    @PostConstruct
    public void init() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .build()
            );
            if(bucketExists) {
                log.info("Bucket exists!");
            }else{
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucketName()).build());
                log.info("Make bucket successfully!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public String uploadFile(MultipartFile file) {
        if(file.isEmpty()){
            throw new IllegalArgumentException("文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        String objectName = "Verification/"+ IdUtil.simpleUUID()+suffix;
        String bucketName = minioConfig.getBucketName();
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("图片上传成功，桶：{}，路径：{}",bucketName , objectName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return objectName;
    }
    public String getPresignedUrl(String objectName) {
        try{
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .expiry(minioConfig.getExpireTime())
                            .build()
            );
        }catch(Exception e){
            log.info("获取图片预签名URL失败，桶：{}，路径：{}",minioConfig.getBucketName(), objectName);
            throw new RuntimeException("获取图片预签名URL失败");
        }
    }
    public void deleteFile(String objectName) {
        try{
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build()
            );
            log.info("图片删除成功，桶：{}，路径：{}",minioConfig.getBucketName(), objectName);
        }catch(Exception e){
            log.info("图片删除失败，桶：{}，路径：{}",minioConfig.getBucketName(), objectName);
            throw new RuntimeException("图片删除失败");
        }
    }
}
