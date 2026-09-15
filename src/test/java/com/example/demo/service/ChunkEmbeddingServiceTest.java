package com.example.demo.service;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.dto.BatchEmbeddingResult;
import com.example.demo.dto.ChunkEmbeddingDraft;
import com.example.demo.exception.EmbeddingCallException;
import com.example.demo.model.AIUsageLog;
import com.example.demo.model.DocumentChunk;
import com.example.demo.model.FileRecord;
import com.example.demo.model.User;
import com.example.demo.repository.AIUsageLogRepository;
import com.example.demo.repository.DocumentChunkRepository;
import com.example.demo.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * ChunkEmbeddingService 编排测试 —— 分批、映射、AIUsageLog 记录。
 */
@ExtendWith(MockitoExtension.class)
class ChunkEmbeddingServiceTest {

    @Mock private DocumentChunkRepository chunkRepository;
    @Mock private FileRepository fileRepository;
    @Mock private EmbeddingService embeddingService;
    @Mock private AIUsageLogRepository usageLogRepository;

    private EmbeddingProperties embeddingProperties;
    private ChunkEmbeddingService service;

    @BeforeEach
    void setUp() {
        embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setBatchSize(32);
        embeddingProperties.setDimension(4);
        service = new ChunkEmbeddingService(chunkRepository, fileRepository, embeddingService,
                embeddingProperties, usageLogRepository);
    }

    private DocumentChunk chunk(int index) {
        DocumentChunk c = new DocumentChunk(null, index, "content-" + index, 8);
        c.setId((long) (100 + index));
        return c;
    }

    @Test
    @DisplayName("70 Chunk + batchSize=32 → 3 批（32/32/6），映射 chunkId/chunkIndex 正确，记 3 条日志")
    void batchesAndMapsCorrectly() {
        FileRecord record = new FileRecord(new User(1L, "alice", "pwd"), "a.txt",
                "1/2026/08/a.txt", "txt", "text/plain", 10, "hash");
        record.setId(1L);
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(record));

        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 70; i++) chunks.add(chunk(i));
        when(chunkRepository.findByFileIdOrderByChunkIndexAsc(1L)).thenReturn(chunks);

        // mock embedBatch：按输入数量返回对应 index 的向量
        when(embeddingService.embedBatch(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            List<BatchEmbeddingResult.EmbeddingVector> vectors = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                vectors.add(new BatchEmbeddingResult.EmbeddingVector(i, new float[4]));
            }
            return new BatchEmbeddingResult("mock", "mock-embedding", 4, vectors, 10, 0);
        });

        List<ChunkEmbeddingDraft> drafts = service.embedFile(1L);

        assertEquals(70, drafts.size());
        // 映射：draft.get(i) 对应 chunkIndex=i
        assertEquals(0, drafts.get(0).chunkIndex());
        assertEquals(69, drafts.get(69).chunkIndex());
        assertEquals(100L, drafts.get(0).chunkId());
        assertEquals(169L, drafts.get(69).chunkId());

        // 3 次调用，输入大小 32/32/6
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(embeddingService, times(3)).embedBatch(captor.capture());
        assertEquals(32, captor.getAllValues().get(0).size());
        assertEquals(32, captor.getAllValues().get(1).size());
        assertEquals(6, captor.getAllValues().get(2).size());

        // 3 条日志，callType=EMBEDDING
        ArgumentCaptor<AIUsageLog> logCaptor = ArgumentCaptor.forClass(AIUsageLog.class);
        verify(usageLogRepository, times(3)).save(logCaptor.capture());
        for (AIUsageLog entry : logCaptor.getAllValues()) {
            assertEquals("EMBEDDING", entry.getCallType());
            assertEquals(1L, entry.getFileId());
            assertEquals(1L, entry.getUserId());
            assertTrue(entry.isSuccess());
        }
    }

    @Test
    @DisplayName("空 Chunk 列表 → 0 draft，不调用 embedBatch")
    void emptyChunksNoCall() {
        FileRecord record = new FileRecord(new User(1L, "alice", "pwd"), "a.txt",
                "1/2026/08/a.txt", "txt", "text/plain", 10, "hash");
        record.setId(1L);
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(record));
        when(chunkRepository.findByFileIdOrderByChunkIndexAsc(1L)).thenReturn(List.of());

        List<ChunkEmbeddingDraft> drafts = service.embedFile(1L);

        assertTrue(drafts.isEmpty());
        verify(embeddingService, never()).embedBatch(anyList());
    }

    @Test
    @DisplayName("失败日志记录真实 retryCount")
    void failureLogsRealRetryCount() {
        FileRecord record = new FileRecord(new User(1L, "alice", "pwd"), "a.txt",
                "1/2026/08/a.txt", "txt", "text/plain", 10, "hash");
        record.setId(1L);
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(record));
        when(chunkRepository.findByFileIdOrderByChunkIndexAsc(1L)).thenReturn(List.of(chunk(0)));
        when(embeddingService.embedBatch(anyList()))
                .thenThrow(new EmbeddingCallException("rate_limit", "限流", 2));

        assertThrows(EmbeddingCallException.class, () -> service.embedFile(1L));

        ArgumentCaptor<AIUsageLog> captor = ArgumentCaptor.forClass(AIUsageLog.class);
        verify(usageLogRepository).save(captor.capture());
        AIUsageLog entry = captor.getValue();
        assertFalse(entry.isSuccess());
        assertEquals("rate_limit", entry.getErrorType());
        assertEquals(2, entry.getRetryCount());
        assertEquals("EMBEDDING", entry.getCallType());
    }
}
