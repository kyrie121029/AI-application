package com.example.demo.service;

import com.example.demo.config.ImageAnalysisProperties;
import com.example.demo.enums.ImageAnalysisStatus;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.ImageAnalysisConflictException;
import com.example.demo.exception.InvalidFileException;
import com.example.demo.model.FileRecord;
import com.example.demo.model.ImageAnalysis;
import com.example.demo.model.User;
import com.example.demo.repository.FileRepository;
import com.example.demo.repository.ImageAnalysisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ImageAnalysisService 编排测试 —— 状态校验、非图片拒绝、归属隔离、线程池拒绝补偿。
 */
@ExtendWith(MockitoExtension.class)
class ImageAnalysisServiceTest {

    @Mock private FileRepository fileRepository;
    @Mock private ImageAnalysisRepository imageAnalysisRepository;
    @Mock private ImageAnalysisPersistenceService persistence;
    @Mock private ImageAnalysisWorker worker;
    @Mock private ImageAnalysisProperties properties;
    @InjectMocks private ImageAnalysisService service;

    private final User alice = new User(1L, "alice", "pwd");
    private final User bob = new User(2L, "bob", "pwd");

    private FileRecord image(User owner) {
        FileRecord r = new FileRecord(owner, "a.jpg", "1/2026/08/a.jpg", "jpg",
                "image/jpeg", 10, "hash");
        r.setId(1L);
        return r;
    }

    private ImageAnalysis analysis(ImageAnalysisStatus status) {
        ImageAnalysis a = new ImageAnalysis(image(alice));
        a.setId(1L);
        a.setStatus(status);
        return a;
    }

    @Test
    @DisplayName("首次提交：无记录 → createPending + 触发异步")
    void firstSubmitCreatesAndRuns() {
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(image(alice)));
        when(imageAnalysisRepository.findByFileId(1L)).thenReturn(Optional.empty());

        service.submitAnalysis(1L, alice);

        verify(persistence).createPending(1L);
        verify(worker).runAsync(1L);
    }

    @Test
    @DisplayName("FAILED 重试：resetToPending 返回 1 → 触发异步")
    void failedRetryRuns() {
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(image(alice)));
        when(imageAnalysisRepository.findByFileId(1L)).thenReturn(Optional.of(analysis(ImageAnalysisStatus.FAILED)));
        when(persistence.resetToPending(1L)).thenReturn(1);

        service.submitAnalysis(1L, alice);

        verify(worker).runAsync(1L);
    }

    @Test
    @DisplayName("PROCESSING 禁止重复提交 → ImageAnalysisConflictException")
    void processingRejected() {
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(image(alice)));
        when(imageAnalysisRepository.findByFileId(1L)).thenReturn(Optional.of(analysis(ImageAnalysisStatus.PROCESSING)));
        when(persistence.resetToPending(1L)).thenReturn(0);

        assertThrows(ImageAnalysisConflictException.class, () -> service.submitAnalysis(1L, alice));
        verify(worker, never()).runAsync(any());
    }

    @Test
    @DisplayName("SUCCESS 默认不重复分析 → ImageAnalysisConflictException")
    void successRejected() {
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(image(alice)));
        when(imageAnalysisRepository.findByFileId(1L)).thenReturn(Optional.of(analysis(ImageAnalysisStatus.SUCCESS)));
        when(persistence.resetToPending(1L)).thenReturn(0);

        assertThrows(ImageAnalysisConflictException.class, () -> service.submitAnalysis(1L, alice));
        verify(worker, never()).runAsync(any());
    }

    @Test
    @DisplayName("非图片文件 → InvalidFileException")
    void nonImageRejected() {
        FileRecord pdf = new FileRecord(alice, "a.pdf", "1/2026/08/a.pdf", "pdf",
                "application/pdf", 10, "hash");
        pdf.setId(1L);
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(pdf));

        assertThrows(InvalidFileException.class, () -> service.submitAnalysis(1L, alice));
    }

    @Test
    @DisplayName("他人图片 → ForbiddenException")
    void otherUserForbidden() {
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(image(bob)));

        assertThrows(ForbiddenException.class, () -> service.submitAnalysis(1L, alice));
        verify(worker, never()).runAsync(any());
    }

    @Test
    @DisplayName("线程池拒绝 → markFailed 补偿，不留永久 PENDING")
    void rejectionCompensatesToFailed() {
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(image(alice)));
        when(imageAnalysisRepository.findByFileId(1L)).thenReturn(Optional.empty());
        doThrow(new RejectedExecutionException("queue full")).when(worker).runAsync(1L);

        assertThrows(ImageAnalysisConflictException.class, () -> service.submitAnalysis(1L, alice));
        verify(persistence).markFailed(eq(1L), contains("线程池繁忙"));
    }
}
