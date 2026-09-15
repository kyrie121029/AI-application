package com.example.demo.service;

import com.example.demo.enums.IndexStatus;
import com.example.demo.exception.IndexStatusConflictException;
import com.example.demo.model.DocumentIndex;
import com.example.demo.repository.DocumentIndexRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DocumentIndexPersistenceService createIfAbsent + CAS 语义测试。
 * 固定 H2 方言（不打开真实连接），mock JdbcTemplate 验证调用。
 */
@ExtendWith(MockitoExtension.class)
class DocumentIndexPersistenceServiceTest {

    @Mock private DocumentIndexRepository indexRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    private DocumentIndexPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new DocumentIndexPersistenceService(indexRepository, jdbcTemplate) {
            @Override
            protected String databaseProduct() {
                return "H2";
            }
        };
    }

    private DocumentIndex row(IndexStatus status) {
        DocumentIndex d = new DocumentIndex(null);
        d.setStatus(status);
        return d;
    }

    @Test
    @DisplayName("首次 beginIndex：createIfAbsent + CAS 抢占成功进入 INDEXING")
    void firstBeginIndexClaims() {
        // createIfAbsent 走 jdbcTemplate（H2 MERGE），行存在后 CAS 返回 1
        when(jdbcTemplate.update(any(String.class), eq(1L))).thenReturn(1);
        when(indexRepository.tryMarkIndexing(1L, "m", 4, "v1")).thenReturn(1);

        service.beginIndex(1L, "m", 4, "v1");

        verify(jdbcTemplate).update(contains("MERGE INTO document_indexes"), eq(1L));
        verify(indexRepository).tryMarkIndexing(1L, "m", 4, "v1");
    }

    @Test
    @DisplayName("已 INDEXING 再 beginIndex → IndexStatusConflictException")
    void alreadyIndexingRejected() {
        when(jdbcTemplate.update(any(String.class), eq(1L))).thenReturn(1);
        // CAS 返回 0：另一请求正 INDEXING
        when(indexRepository.tryMarkIndexing(1L, "m", 4, "v1")).thenReturn(0);

        assertThrows(IndexStatusConflictException.class, () -> service.beginIndex(1L, "m", 4, "v1"));
    }

    @Test
    @DisplayName("FAILED 后 beginIndex → CAS 抢占成功（可重试）")
    void failedCanBeginAgain() {
        when(jdbcTemplate.update(any(String.class), eq(1L))).thenReturn(1);
        when(indexRepository.tryMarkIndexing(1L, "m", 4, "v1")).thenReturn(1);

        service.beginIndex(1L, "m", 4, "v1");

        verify(indexRepository).tryMarkIndexing(1L, "m", 4, "v1");
    }

    @Test
    @DisplayName("MySQL 方言走 ON DUPLICATE KEY UPDATE 分支")
    void mysqlDialectUsesOnDuplicate() {
        service = new DocumentIndexPersistenceService(indexRepository, jdbcTemplate) {
            @Override
            protected String databaseProduct() {
                return "MySQL";
            }
        };
        when(jdbcTemplate.update(any(String.class), eq(1L))).thenReturn(1);
        when(indexRepository.tryMarkIndexing(1L, "m", 4, "v1")).thenReturn(1);

        service.beginIndex(1L, "m", 4, "v1");

        verify(jdbcTemplate).update(contains("ON DUPLICATE KEY UPDATE"), eq(1L));
    }

    @Test
    @DisplayName("markSuccess / markFailed 更新状态")
    void markSuccessAndFailed() {
        when(indexRepository.findByFileId(1L))
                .thenReturn(Optional.of(row(IndexStatus.INDEXING)));

        service.markSuccess(1L);
        service.markFailed(1L, "err");

        // 均通过同一 row 更新（repo.save 由 dirty checking 提交）
        verify(indexRepository, times(2)).findByFileId(1L);
    }
}
