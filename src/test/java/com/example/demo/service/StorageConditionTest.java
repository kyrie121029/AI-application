package com.example.demo.service;

import com.example.demo.config.StorageProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 存储实现条件装配测试 —— 验证 storage.type 在 local/minio 之间切换时，
 * 只有对应的 StorageService 实现被装配。
 */
class StorageConditionTest {

    private ApplicationContextRunner runner(String type) {
        return new ApplicationContextRunner()
                .withPropertyValues("storage.type=" + type)
                .withBean(StorageProperties.class, StorageProperties::new)
                // MinioStorageService 需要 MinioClient 依赖；用 mock 避免真实连接
                .withBean(MinioClient.class, () -> mock(MinioClient.class))
                .withUserConfiguration(LocalStorageService.class, MinioStorageService.class);
    }

    @Test
    @DisplayName("storage.type=local → 装配 LocalStorageService，不装配 MinioStorageService")
    void localLoadsLocalStorage() {
        runner("local").run(ctx -> {
            assertTrue(ctx.containsBean("localStorageService"));
            assertFalse(ctx.containsBean("minioStorageService"));
        });
    }

    @Test
    @DisplayName("storage.type=minio → 装配 MinioStorageService，不装配 LocalStorageService")
    void minioLoadsMinioStorage() {
        runner("minio").run(ctx -> {
            assertTrue(ctx.containsBean("minioStorageService"));
            assertFalse(ctx.containsBean("localStorageService"));
        });
    }
}
