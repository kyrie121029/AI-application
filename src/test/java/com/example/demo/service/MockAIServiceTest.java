package com.example.demo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockAIService 流式实现测试。
 * <p>
 * 验证：实现最新版 AIService 接口后，retryCountOut 固定写 0、
 * 正常完成回调 onComplete、取消逻辑保留。
 */
class MockAIServiceTest {

    @Test
    @DisplayName("Mock 流式完成 → retryCountOut=0，输出 chunk，触发 onComplete")
    void retryCountOutIsAlwaysZero() throws Exception {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(5);
        executor.initialize();
        try {
            MockAIService svc = new MockAIService(executor);
            AtomicInteger retryOut = new AtomicInteger(-1);
            AtomicInteger chunkCount = new AtomicInteger(0);
            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean cancelled = new AtomicBoolean(false);

            svc.streamAnalyze("system", List.of(), cancelled::get,
                    chunk -> chunkCount.incrementAndGet(),
                    done::countDown,
                    err -> fail("Mock 正常路径不应触发 onError: " + err),
                    retryOut);

            assertTrue(done.await(5, TimeUnit.SECONDS), "Mock 流式应在 5s 内完成");
            assertEquals(0, retryOut.get(), "Mock 不重试，retryCountOut 应为 0");
            assertTrue(chunkCount.get() > 0, "应输出至少一个 chunk");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Mock 流式取消 → 不触发 onComplete/onError，retryCountOut 保持 0")
    void cancelStopsStreamWithoutCallbacks() throws Exception {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(5);
        executor.initialize();
        try {
            MockAIService svc = new MockAIService(executor);
            AtomicInteger retryOut = new AtomicInteger(-1);
            AtomicBoolean cancelled = new AtomicBoolean(false);
            AtomicBoolean onCompleteFired = new AtomicBoolean(false);
            AtomicBoolean onErrorFired = new AtomicBoolean(false);
            AtomicInteger chunkCount = new AtomicInteger(0);

            svc.streamAnalyze("system", List.of(), cancelled::get,
                    chunk -> {
                        // 首个 chunk 后立即标记取消（模拟客户端断开）
                        cancelled.set(true);
                        chunkCount.incrementAndGet();
                    },
                    () -> onCompleteFired.set(true),
                    err -> onErrorFired.set(true),
                    retryOut);

            // 等待取消生效后循环提前退出
            Thread.sleep(200);
            assertFalse(onCompleteFired.get(), "取消后不应触发 onComplete");
            assertFalse(onErrorFired.get(), "取消后不应触发 onError");
            assertEquals(0, retryOut.get(), "retryCountOut 应固定为 0");
        } finally {
            executor.shutdown();
        }
    }
}
