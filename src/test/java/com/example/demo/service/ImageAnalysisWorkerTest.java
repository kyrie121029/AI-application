package com.example.demo.service;

import com.example.demo.config.ImageAnalysisProperties;
import com.example.demo.dto.ImageAnalysisResult;
import com.example.demo.exception.MultimodalAIException;
import com.example.demo.model.AIUsageLog;
import com.example.demo.model.FileRecord;
import com.example.demo.model.User;
import com.example.demo.repository.AIUsageLogRepository;
import com.example.demo.repository.FileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ImageAnalysisWorker 单元测试 —— mock Storage / MultimodalAIService / Persistence。
 */
@ExtendWith(MockitoExtension.class)
class ImageAnalysisWorkerTest {

    @Mock private FileRepository fileRepository;
    @Mock private StorageService storageService;
    @Mock private MultimodalAIService multimodalAIService;
    @Mock private ImageAnalysisPersistenceService persistence;
    @Mock private ImageAnalysisProperties properties;
    @Mock private AIUsageLogRepository usageLogRepository;
    @InjectMocks private ImageAnalysisWorker worker;

    private FileRecord record() {
        FileRecord r = new FileRecord(new User(1L, "alice", "pwd"), "a.jpg",
                "1/2026/08/a.jpg", "jpg", "image/jpeg", 10, "hash");
        r.setId(1L);
        return r;
    }

    private ImageAnalysisResult result() {
        return new ImageAnalysisResult("summary", List.of("obj"), "scene", "text", List.of());
    }

    private void stubLoadable() {
        when(persistence.markProcessing(1L)).thenReturn(1); // 状态转换成功
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(record()));
        when(storageService.load("1/2026/08/a.jpg")).thenReturn(new InputStreamResource(
                new ByteArrayInputStream("fake-image".getBytes(StandardCharsets.UTF_8))));
        when(multimodalAIService.getModelName()).thenReturn("mock-multimodal");
        when(multimodalAIService.getProvider()).thenReturn("mock");
        when(multimodalAIService.analyzeImage(any(), any(), any())).thenReturn(result());
    }

    @Test
    @DisplayName("分析成功：PROCESSING → SUCCESS，保存结果")
    void successFlow() {
        stubLoadable();

        worker.runAsync(1L);

        InOrder inOrder = inOrder(persistence);
        inOrder.verify(persistence).markProcessing(1L);
        inOrder.verify(persistence).markSuccess(eq(1L), any(), any());
        verify(persistence, never()).markFailed(any(), any());
        verify(usageLogRepository).save(any());
    }

    @Test
    @DisplayName("多模态模型失败：PROCESSING → FAILED，保存失败原因")
    void modelFailureFlowsToFailed() {
        stubLoadable();
        when(multimodalAIService.analyzeImage(any(), any(), any()))
                .thenThrow(new MultimodalAIException("模型调用失败"));

        worker.runAsync(1L);

        InOrder inOrder = inOrder(persistence);
        inOrder.verify(persistence).markProcessing(1L);
        inOrder.verify(persistence).markFailed(eq(1L), eq("模型调用失败"));
        verify(persistence, never()).markSuccess(any(), any(), any());
    }

    @Test
    @DisplayName("StorageService.load 失败：PROCESSING → FAILED")
    void loadFailureFlowsToFailed() {
        when(persistence.markProcessing(1L)).thenReturn(1);
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(record()));
        when(storageService.load("1/2026/08/a.jpg"))
                .thenThrow(new com.example.demo.exception.FileStorageException("读取失败"));

        worker.runAsync(1L);

        verify(persistence).markProcessing(1L);
        verify(persistence).markFailed(eq(1L), contains("读取失败"));
        verify(persistence, never()).markSuccess(any(), any(), any());
    }

    @Test
    @DisplayName("markProcessing 返回 0（状态被抢占）→ 中止，不调用模型")
    void markProcessingZeroAborts() {
        when(persistence.markProcessing(1L)).thenReturn(0);

        worker.runAsync(1L);

        verify(persistence, never()).markSuccess(any(), any(), any());
        verify(multimodalAIService, never()).analyzeImage(any(), any(), any());
        verify(storageService, never()).load(any());
    }

    @Test
    @DisplayName("AIUsageLog 保存失败不影响 SUCCESS（不会回写成 FAILED）")
    void usageLogFailureDoesNotChangeSuccess() {
        stubLoadable();
        doThrow(new RuntimeException("log save fail")).when(usageLogRepository).save(any());

        worker.runAsync(1L);

        verify(persistence).markSuccess(eq(1L), any(), any());
        verify(persistence, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("失败日志：errorType 分类 + createdAt 初始化")
    void failureLogClassifiesErrorTypeAndCreatedAt() {
        stubLoadable();
        when(multimodalAIService.analyzeImage(any(), any(), any()))
                .thenThrow(new MultimodalAIException("模型调用失败"));

        worker.runAsync(1L);

        ArgumentCaptor<AIUsageLog> captor = ArgumentCaptor.forClass(AIUsageLog.class);
        verify(usageLogRepository).save(captor.capture());
        assertEquals("model", captor.getValue().getErrorType());
        assertNotNull(captor.getValue().getCreatedAt());
    }
}
