package com.example.demo.service;

import com.example.demo.config.RagProperties;
import com.example.demo.dto.ChunkEmbeddingDraft;
import com.example.demo.exception.IndexStatusConflictException;
import com.example.demo.exception.VectorStoreException;
import com.example.demo.model.FileRecord;
import com.example.demo.model.User;
import com.example.demo.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DocumentIndexService 编排测试 —— 状态流转、embed→delete→upsert 顺序、失败一致性、并发拒绝。
 */
@ExtendWith(MockitoExtension.class)
class DocumentIndexServiceTest {

    @Mock private FileRepository fileRepository;
    @Mock private ChunkEmbeddingService chunkEmbeddingService;
    @Mock private VectorStore vectorStore;
    @Mock private DocumentIndexPersistenceService persistence;

    private RagProperties ragProperties;
    private DocumentIndexService service;

    private final User alice = new User(1L, "alice", "pwd");
    private final User bob = new User(2L, "bob", "pwd");

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.getIndex().setVersion("rag-index-v1");
        service = new DocumentIndexService(fileRepository, chunkEmbeddingService, vectorStore,
                ragProperties, persistence);
    }

    private FileRecord record(User owner) {
        FileRecord r = new FileRecord(owner, "a.txt", "1/2026/08/a.txt", "txt",
                "text/plain", 10, "hash");
        r.setId(1L);
        return r;
    }

    private ChunkEmbeddingDraft draft(Long chunkId, int chunkIndex) {
        return new ChunkEmbeddingDraft(chunkId, chunkIndex, new float[4], "mock-embedding", 4);
    }

    private void stubCommon() {
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(record(alice)));
        when(chunkEmbeddingService.currentModel()).thenReturn("mock-embedding");
        when(chunkEmbeddingService.currentDimension()).thenReturn(4);
    }

    @Test
    @DisplayName("正常顺序：beginIndex → embed → delete → upsert → SUCCESS")
    void successOrder() {
        stubCommon();
        when(chunkEmbeddingService.embedFile(1L))
                .thenReturn(List.of(draft(101L, 0), draft(102L, 1)));

        service.indexFile(1L, alice);

        InOrder inOrder = inOrder(persistence, chunkEmbeddingService, vectorStore);
        inOrder.verify(persistence).beginIndex(1L, "mock-embedding", 4, "rag-index-v1");
        inOrder.verify(chunkEmbeddingService).embedFile(1L);
        inOrder.verify(vectorStore).deleteByFileId(1L);
        inOrder.verify(vectorStore).upsert(anyList());
        inOrder.verify(persistence).markSuccess(1L);
        verify(persistence, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("Vector Store 失败 → 标记 FAILED，不上抛覆盖")
    void vectorStoreFailureMarksFailed() {
        stubCommon();
        when(chunkEmbeddingService.embedFile(1L)).thenReturn(List.of(draft(101L, 0)));
        doThrow(new VectorStoreException("Qdrant 不可达")).when(vectorStore).upsert(any());

        VectorStoreException e = assertThrows(VectorStoreException.class, () -> service.indexFile(1L, alice));

        verify(persistence).markFailed(eq(1L), contains("Qdrant 不可达"));
        verify(persistence, never()).markSuccess(any());
    }

    @Test
    @DisplayName("Embedding 失败 → 标记 FAILED，且不调用 deleteByFileId（旧索引不受影响）")
    void embeddingFailureDoesNotDelete() {
        stubCommon();
        when(chunkEmbeddingService.embedFile(1L))
                .thenThrow(new com.example.demo.exception.EmbeddingCallException("rate_limit", "限流", 2));

        assertThrows(com.example.demo.exception.EmbeddingCallException.class,
                () -> service.indexFile(1L, alice));

        verify(persistence).markFailed(eq(1L), contains("限流"));
        verify(vectorStore, never()).deleteByFileId(any());
        verify(vectorStore, never()).upsert(any());
    }

    @Test
    @DisplayName("并发：另一请求正在 INDEXING → beginIndex 抛冲突，不继续")
    void concurrentIndexRejected() {
        stubCommon();
        doThrow(new IndexStatusConflictException("文件正在建立索引中，请勿重复提交"))
                .when(persistence).beginIndex(eq(1L), any(), anyInt(), any());

        assertThrows(IndexStatusConflictException.class, () -> service.indexFile(1L, alice));

        verify(chunkEmbeddingService, never()).embedFile(any());
        verify(vectorStore, never()).deleteByFileId(any());
    }

    @Test
    @DisplayName("FAILED 后可重新 index（第一次失败，第二次成功）")
    void failedCanRetry() {
        stubCommon();
        when(chunkEmbeddingService.embedFile(1L)).thenReturn(List.of(draft(101L, 0)));
        // 第一次 upsert 抛错，第二次成功
        int[] calls = {0};
        doAnswer(inv -> {
            if (calls[0]++ == 0) throw new VectorStoreException("Qdrant 不可达");
            return null;
        }).when(vectorStore).upsert(anyList());

        // 第一次失败 → FAILED
        assertThrows(VectorStoreException.class, () -> service.indexFile(1L, alice));
        verify(persistence).markFailed(eq(1L), contains("Qdrant 不可达"));

        // 第二次成功 → SUCCESS
        service.indexFile(1L, alice);
        verify(persistence, times(1)).markSuccess(1L);
        verify(persistence, times(1)).markFailed(any(), any());
    }

    @Test
    @DisplayName("空 Chunk（无 draft）→ 不调 upsert，仍标记 SUCCESS")
    void emptyDraftsNoUpsert() {
        stubCommon();
        when(chunkEmbeddingService.embedFile(1L)).thenReturn(List.of());

        service.indexFile(1L, alice);

        verify(vectorStore, never()).upsert(anyList());
        verify(persistence).markSuccess(1L);
    }

    @Test
    @DisplayName("他人文件 → ForbiddenException")
    void otherUserForbidden() {
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(record(bob)));

        assertThrows(com.example.demo.exception.ForbiddenException.class,
                () -> service.indexFile(1L, alice));
        verify(persistence, never()).beginIndex(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("deleteVectorIndex：Vector Store 不可达不抛异常")
    void deleteVectorIndexBestEffort() {
        doThrow(new VectorStoreException("Qdrant 不可达")).when(vectorStore).deleteByFileId(1L);

        assertDoesNotThrow(() -> service.deleteVectorIndex(1L));
    }
}
