package com.example.demo.service;

import com.example.demo.config.AIProperties;
import com.example.demo.exception.AIServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAIAIService 流式重试编排测试。
 * <p>
 * 通过匿名子类覆写 doStreamCall / waitForRetryDelay 隔离真实 HTTP，
 * 只测 streamAnalyze 提交线程池后的：重试次数写回、退避期间取消、
 * 首 chunk 后不重试、重试耗尽保留真实 errorType。
 */
class OpenAIAIStreamRetryTest {

    private AIProperties props;
    private ThreadPoolTaskExecutor executor;

    /** doStreamCall 行为脚本：按调用次数依次执行 */
    private List<StreamCallBehavior> behaviors;
    private int behaviorIndex;
    private int callCount;
    private CountDownLatch retryScheduled;
    private AtomicBoolean cancelDuringBackoff;
    private AtomicBoolean cancelled;

    @BeforeEach
    void setUp() {
        props = new AIProperties();
        props.getOpenai().setModel("test-model");
        props.setMaxRetries(2);
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.initialize();
        behaviorIndex = 0;
        callCount = 0;
        retryScheduled = new CountDownLatch(1);
        cancelDuringBackoff = new AtomicBoolean(false);
        cancelled = new AtomicBoolean(false);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @FunctionalInterface
    interface StreamCallBehavior {
        void run(Consumer<String> onChunk, Runnable onComplete) throws IOException;
    }

    private OpenAIAIService newService() {
        return new OpenAIAIService(props, executor) {
            @Override
            protected void waitForRetryDelay(long delayMs) throws InterruptedException {
                if (cancelDuringBackoff.get()) {
                    cancelled.set(true); // 模拟退避等待期间客户端断开
                }
                retryScheduled.countDown();
            }

            @Override
            void doStreamCall(Map<String, Object> requestBody, BooleanSupplier isCancelled,
                              Consumer<String> onChunk, Runnable onComplete) throws IOException {
                callCount++;
                if (behaviorIndex < behaviors.size()) {
                    behaviors.get(behaviorIndex++).run(onChunk, onComplete);
                } else {
                    onComplete.run();
                }
            }
        };
    }

    private static class StreamResult {
        int retryCount = -1;
        boolean completed;
        boolean onErrorFired;
        String errorType;
    }

    private StreamResult runStream(List<StreamCallBehavior> behaviors) throws Exception {
        this.behaviors = behaviors;
        CountDownLatch done = new CountDownLatch(1);
        StreamResult r = new StreamResult();
        AtomicInteger retryOut = new AtomicInteger(-1);
        OpenAIAIService svc = newService();
        svc.streamAnalyze("system", List.of(), cancelled::get,
                chunk -> {},
                () -> { r.completed = true; done.countDown(); },
                err -> {
                    r.onErrorFired = true;
                    if (err instanceof AIServiceException ae) {
                        r.errorType = ae.getErrorType();
                    }
                    done.countDown();
                },
                retryOut);
        assertTrue(done.await(5, TimeUnit.SECONDS), "流式调用未在 5s 内结束");
        r.retryCount = retryOut.get();
        return r;
    }

    // ==================== retryCount 写回 ====================

    @Test
    @DisplayName("首次尝试成功 → retryCount=0，仅一次调用")
    void successOnFirstAttemptRecordsZeroRetries() throws Exception {
        StreamResult r = runStream(List.<StreamCallBehavior>of(
                (onChunk, onComplete) -> onComplete.run()));

        assertEquals(1, callCount);
        assertEquals(0, r.retryCount);
        assertTrue(r.completed);
        assertFalse(r.onErrorFired);
    }

    @Test
    @DisplayName("失败一次后成功 → retryCount=1")
    void retryOnceThenSuccess() throws Exception {
        StreamResult r = runStream(List.<StreamCallBehavior>of(
                (onChunk, onComplete) -> { throw new AIServiceException("rate_limit", "限流1"); },
                (onChunk, onComplete) -> onComplete.run()));

        assertEquals(2, callCount);
        assertEquals(1, r.retryCount);
        assertTrue(r.completed);
        assertFalse(r.onErrorFired);
    }

    @Test
    @DisplayName("失败两次后成功 → retryCount=2")
    void retryTwiceThenSuccess() throws Exception {
        StreamResult r = runStream(List.<StreamCallBehavior>of(
                (onChunk, onComplete) -> { throw new AIServiceException("rate_limit", "限流1"); },
                (onChunk, onComplete) -> { throw new AIServiceException("rate_limit", "限流2"); },
                (onChunk, onComplete) -> onComplete.run()));

        assertEquals(3, callCount);
        assertEquals(2, r.retryCount);
        assertTrue(r.completed);
        assertFalse(r.onErrorFired);
    }

    // ==================== 重试耗尽 / 首 chunk / 取消 ====================

    @Test
    @DisplayName("rate_limit 重试耗尽 → errorType 仍为 rate_limit，retryCount=maxRetries")
    void rateLimitExhaustedKeepsRateLimit() throws Exception {
        StreamResult r = runStream(List.<StreamCallBehavior>of(
                (onChunk, onComplete) -> { throw new AIServiceException("rate_limit", "限流1"); },
                (onChunk, onComplete) -> { throw new AIServiceException("rate_limit", "限流2"); },
                (onChunk, onComplete) -> { throw new AIServiceException("rate_limit", "限流3"); }));

        assertEquals(3, callCount); // 首次 + maxRetries(2) 次重试
        assertEquals(2, r.retryCount);
        assertTrue(r.onErrorFired);
        assertEquals("rate_limit", r.errorType, "重试耗尽后应保留真实 errorType");
        assertFalse(r.completed);
    }

    @Test
    @DisplayName("已输出首 chunk 后失败 → 不重试，直接 error")
    void noRetryAfterFirstChunk() throws Exception {
        StreamResult r = runStream(List.<StreamCallBehavior>of(
                (onChunk, onComplete) -> {
                    onChunk.accept("你好");
                    throw new AIServiceException("upstream", "503");
                }));

        assertEquals(1, callCount, "已输出 chunk 后不应再重试");
        assertEquals(0, r.retryCount);
        assertTrue(r.onErrorFired);
        assertEquals("upstream", r.errorType);
        assertFalse(r.completed);
    }

    @Test
    @DisplayName("退避等待期间取消 → 不再发起下一次 HTTP 请求")
    void cancelDuringBackoffSkipsNextRequest() throws Exception {
        cancelDuringBackoff.set(true);
        this.behaviors = List.<StreamCallBehavior>of(
                (onChunk, onComplete) -> { throw new AIServiceException("rate_limit", "模拟限流"); });

        OpenAIAIService svc = newService();
        AtomicInteger retryOut = new AtomicInteger(0);
        AtomicBoolean onCompleteFired = new AtomicBoolean(false);
        AtomicBoolean onErrorFired = new AtomicBoolean(false);
        svc.streamAnalyze("system", List.of(), cancelled::get,
                chunk -> {},
                () -> onCompleteFired.set(true),
                err -> onErrorFired.set(true),
                retryOut);

        assertTrue(retryScheduled.await(5, TimeUnit.SECONDS), "应进入重试退避等待");
        Thread.sleep(50); // 等待 worker 线程跑完重试前的取消检查
        assertEquals(1, callCount, "取消后不应再发起第二次请求");
        assertFalse(onCompleteFired.get());
        assertFalse(onErrorFired.get());
    }

    // ==================== 网络异常分类与重试 ====================

    @Test
    @DisplayName("socket 超时 → 分类为 timeout 且可重试")
    void socketTimeoutMapsToRetryableTimeout() {
        AIServiceException e = OpenAIAIService.toStreamNetworkError(
                new ResourceAccessException("read timeout", new SocketTimeoutException()));
        assertEquals("timeout", e.getErrorType());
        assertTrue(OpenAIAIService.isRetryable(e.getErrorType()));
    }

    @Test
    @DisplayName("连接拒绝 → 分类为 network 且可重试")
    void connectRefusedMapsToRetryableNetwork() {
        AIServiceException e = OpenAIAIService.toStreamNetworkError(
                new ResourceAccessException("refused", new ConnectException()));
        assertEquals("network", e.getErrorType());
        assertTrue(OpenAIAIService.isRetryable(e.getErrorType()));
    }

    @Test
    @DisplayName("timeout 失败 → 进入重试，成功后 retryCount=1")
    void timeoutFailureRetriesThenSucceeds() throws Exception {
        StreamResult r = runStream(List.<StreamCallBehavior>of(
                (onChunk, onComplete) -> { throw new AIServiceException("timeout", "读取超时"); },
                (onChunk, onComplete) -> onComplete.run()));

        assertEquals(2, callCount);
        assertEquals(1, r.retryCount);
        assertTrue(r.completed);
        assertFalse(r.onErrorFired);
    }

    @Test
    @DisplayName("network 失败 → 进入重试，成功后 retryCount=1")
    void networkFailureRetriesThenSucceeds() throws Exception {
        StreamResult r = runStream(List.<StreamCallBehavior>of(
                (onChunk, onComplete) -> { throw new AIServiceException("network", "断网"); },
                (onChunk, onComplete) -> onComplete.run()));

        assertEquals(2, callCount);
        assertEquals(1, r.retryCount);
        assertTrue(r.completed);
        assertFalse(r.onErrorFired);
    }

    @Test
    @DisplayName("已输出首 chunk 后 network 失败 → 不重试，直接 error")
    void networkFailureAfterFirstChunkNotRetried() throws Exception {
        StreamResult r = runStream(List.<StreamCallBehavior>of(
                (onChunk, onComplete) -> {
                    onChunk.accept("你好");
                    throw new AIServiceException("network", "断网");
                }));

        assertEquals(1, callCount, "首 chunk 后即使 network 错误也不重试");
        assertEquals(0, r.retryCount);
        assertTrue(r.onErrorFired);
        assertEquals("network", r.errorType);
        assertFalse(r.completed);
    }
}
