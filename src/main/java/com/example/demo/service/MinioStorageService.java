package com.example.demo.service;

import com.example.demo.config.StorageProperties;
import com.example.demo.exception.FileStorageException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * MinIO 对象存储实现 —— 仅在 storage.type=minio 时生效。
 * <p>
 * storageKey 直接作为 MinIO object name（bucket + storageKey），
 * 复用 Local 模式的 storageKey 设计，不引入与存储实现绑定的字段。
 * <p>
 * 流式语义：
 *   - store：putObject 传入 size，SDK 流式上传，不整读内存；
 *   - load：GetObjectResponse 本身是 InputStream，包装为 InputStreamResource 按需流式读取。
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class MinioStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorageService(MinioClient minioClient, StorageProperties props) {
        this.minioClient = minioClient;
        this.bucket = props.getMinio().getBucket();
    }

    @Override
    public void store(String storageKey, InputStream content, long size) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .stream(content, size, -1) // partSize -1 使用 SDK 默认分片
                    .build());
        } catch (Exception e) {
            throw new FileStorageException("MinIO 上传失败: " + storageKey, e);
        }
    }

    @Override
    public Resource load(String storageKey) {
        try {
            GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .build());
            // InputStreamResource 流式读取，contentLength 未知（chunked 传输），对下载无碍
            return new InputStreamResource(response);
        } catch (Exception e) {
            throw new FileStorageException("MinIO 读取失败: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .build());
        } catch (ErrorResponseException e) {
            // 对象不存在：S3 删除语义下应保持幂等，不视为业务失败
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                log.warn("MinIO 对象不存在，忽略删除: {}", storageKey);
                return;
            }
            throw new FileStorageException("MinIO 删除失败: " + storageKey, e);
        } catch (Exception e) {
            throw new FileStorageException("MinIO 删除失败: " + storageKey, e);
        }
    }
}
