package com.example.demo.service;

import com.example.demo.config.StorageProperties;
import com.example.demo.exception.FileStorageException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MinioStorageService 单元测试 —— Mock MinioClient，不依赖真实 MinIO。
 */
@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private MinioStorageService service;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.getMinio().setBucket("ai-app-files");
        service = new MinioStorageService(minioClient, props);
    }

    @Test
    @DisplayName("store：使用正确 bucket 和 storageKey，且 object name 就是 storageKey（不含原文件名）")
    void storeUsesBucketAndStorageKey() throws Exception {
        String key = "12/2026/08/uuid-1234.pdf";

        service.store(key, new ByteArrayInputStream("x".getBytes()), 1);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertEquals("ai-app-files", captor.getValue().bucket());
        assertEquals(key, captor.getValue().object());
    }

    @Test
    @DisplayName("load：按 storageKey 获取对象，返回流式 Resource")
    void loadReturnsStreamingResource() throws Exception {
        String key = "12/2026/08/uuid-1234.pdf";
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

        Resource resource = service.load(key);

        ArgumentCaptor<GetObjectArgs> captor = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minioClient).getObject(captor.capture());
        assertEquals("ai-app-files", captor.getValue().bucket());
        assertEquals(key, captor.getValue().object());
        assertNotNull(resource);
        assertSame(mockResponse, resource.getInputStream(), "Resource 应直接包装 MinIO 流（不整读内存）");
    }

    @Test
    @DisplayName("delete：使用正确 object name")
    void deleteUsesCorrectObjectName() throws Exception {
        String key = "12/2026/08/uuid-1234.pdf";

        service.delete(key);

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertEquals("ai-app-files", captor.getValue().bucket());
        assertEquals(key, captor.getValue().object());
    }

    @Test
    @DisplayName("store：SDK 异常转为 FileStorageException")
    void storeWrapsSdkException() throws Exception {
        doThrow(new RuntimeException("io error")).when(minioClient).putObject(any(PutObjectArgs.class));

        assertThrows(FileStorageException.class,
                () -> service.store("k.pdf", new ByteArrayInputStream("x".getBytes()), 1));
    }

    @Test
    @DisplayName("load：SDK 异常转为 FileStorageException")
    void loadWrapsSdkException() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("io error"));

        assertThrows(FileStorageException.class, () -> service.load("k.pdf"));
    }

    @Test
    @DisplayName("delete：SDK 异常转为 FileStorageException")
    void deleteWrapsSdkException() throws Exception {
        doThrow(new RuntimeException("io error")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThrows(FileStorageException.class, () -> service.delete("k.pdf"));
    }

    @Test
    @DisplayName("delete：对象不存在（NoSuchKey）保持幂等，不抛异常")
    void deleteIsIdempotentWhenObjectMissing() throws Exception {
        ErrorResponseException ex = mock(ErrorResponseException.class);
        ErrorResponse errorResponse = mock(ErrorResponse.class);
        when(errorResponse.code()).thenReturn("NoSuchKey");
        when(ex.errorResponse()).thenReturn(errorResponse);
        doThrow(ex).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertDoesNotThrow(() -> service.delete("missing.pdf"));
    }
}
