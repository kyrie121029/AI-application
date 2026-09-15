package com.example.demo.service;

import com.example.demo.enums.FileParseStatus;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.ParseConflictException;
import com.example.demo.model.FileRecord;
import com.example.demo.model.User;
import com.example.demo.repository.FileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FileParseService 编排测试 —— 状态校验、防重复、归属隔离。
 */
@ExtendWith(MockitoExtension.class)
class FileParseServiceTest {

    @Mock private FileRepository fileRepository;
    @Mock private FileParsePersistenceService persistence;
    @Mock private FileParseWorker worker;
    @InjectMocks private FileParseService service;

    private final User alice = new User(1L, "alice", "pwd");
    private final User bob = new User(2L, "bob", "pwd");

    private FileRecord record(User owner, FileParseStatus status) {
        FileRecord r = new FileRecord(owner, "a.pdf", "1/2026/08/x.pdf", "pdf",
                "application/pdf", 10, "hash");
        r.setId(1L);
        r.setParseStatus(status);
        return r;
    }

    @Test
    @DisplayName("PENDING 允许提交 → markPending + 触发异步")
    void pendingCanBeSubmitted() {
        when(fileRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(record(alice, FileParseStatus.PENDING)));

        service.submitParse(1L, alice);

        verify(persistence).markPending(1L);
        verify(worker).runAsync(1L);
    }

    @Test
    @DisplayName("FAILED 允许重试 → markPending + 触发异步")
    void failedCanBeRetried() {
        when(fileRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(record(alice, FileParseStatus.FAILED)));

        service.submitParse(1L, alice);

        verify(persistence).markPending(1L);
        verify(worker).runAsync(1L);
    }

    @Test
    @DisplayName("PROCESSING 禁止重复提交 → ParseConflictException")
    void processingIsRejected() {
        when(fileRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(record(alice, FileParseStatus.PROCESSING)));

        assertThrows(ParseConflictException.class, () -> service.submitParse(1L, alice));
        verify(persistence, never()).markPending(any());
        verify(worker, never()).runAsync(any());
    }

    @Test
    @DisplayName("SUCCESS 默认不重复解析 → ParseConflictException")
    void successIsRejected() {
        when(fileRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(record(alice, FileParseStatus.SUCCESS)));

        assertThrows(ParseConflictException.class, () -> service.submitParse(1L, alice));
        verify(worker, never()).runAsync(any());
    }

    @Test
    @DisplayName("他人文件无权限解析 → ForbiddenException")
    void otherUserIsForbidden() {
        when(fileRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(record(bob, FileParseStatus.FAILED)));

        assertThrows(ForbiddenException.class, () -> service.submitParse(1L, alice));
        verify(worker, never()).runAsync(any());
    }

    @Test
    @DisplayName("查询解析状态归属校验")
    void getStatusRequiresOwnership() {
        when(fileRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(record(alice, FileParseStatus.SUCCESS)));

        assertDoesNotThrow(() -> service.getParseStatus(1L, alice));

        when(fileRepository.findByIdWithUser(2L))
                .thenReturn(Optional.of(record(bob, FileParseStatus.SUCCESS)));
        assertThrows(ForbiddenException.class, () -> service.getParseStatus(2L, alice));
    }
}
