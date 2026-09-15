package com.example.demo.service;

import com.example.demo.config.RagProperties;
import com.example.demo.dto.EmbeddingResult;
import com.example.demo.dto.RetrievalResult;
import com.example.demo.dto.VectorSearchRequest;
import com.example.demo.dto.VectorSearchResult;
import com.example.demo.exception.InvalidFileException;
import com.example.demo.model.DocumentChunk;
import com.example.demo.model.FileRecord;
import com.example.demo.model.User;
import com.example.demo.repository.DocumentChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RetrievalService 编排测试 —— query→embed→search→owner 二次校验→排序保持 / orphan/越权跳过。
 */
@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStore vectorStore;
    @Mock private DocumentChunkRepository chunkRepository;

    private RagProperties ragProperties;
    private RetrievalService service;

    private final User alice = new User(1L, "alice", "pwd");

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.getRetrieval().setTopK(5);
        ragProperties.getRetrieval().setScoreThreshold(0.0);
        ragProperties.getIndex().setVersion("rag-index-v1");
        service = new RetrievalService(embeddingService, vectorStore, chunkRepository, ragProperties);
    }

    private void stubEmbed() {
        when(embeddingService.embed(anyString())).thenReturn(
                new EmbeddingResult("mock", "mock-embedding", 4, new float[4], null, 0));
    }

    private DocumentChunk chunk(Long id, int index, String content, Long fileId) {
        FileRecord file = new FileRecord(alice, "a.txt", "1/2026/08/a.txt", "txt",
                "text/plain", 10, "hash");
        file.setId(fileId);
        DocumentChunk c = new DocumentChunk(file, index, content, content.length());
        c.setId(id);
        return c;
    }

    @Test
    @DisplayName("空 query 拒绝")
    void rejectsEmptyQuery() {
        assertThrows(InvalidFileException.class, () -> service.search("  ", alice));
        assertThrows(InvalidFileException.class, () -> service.search(null, alice));
        verify(vectorStore, never()).search(any());
    }

    @Test
    @DisplayName("query→embed→search 调用链，request 带 userId/topK/indexVersion")
    void chainAndRequestBuilt() {
        stubEmbed();
        when(vectorStore.search(any())).thenReturn(List.of());

        service.search("问题", alice, List.of(7L));

        ArgumentCaptor<VectorSearchRequest> captor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        verify(vectorStore).search(captor.capture());
        assertEquals(1L, captor.getValue().userId());
        assertEquals(List.of(7L), captor.getValue().fileIds());
        assertEquals(5, captor.getValue().topK());
        assertEquals("rag-index-v1", captor.getValue().indexVersion());
    }

    @Test
    @DisplayName("owner 二次校验：chunk 属于当前用户 → 以 MySQL fileId/chunkIndex/content 为准，score 用 Qdrant")
    void ownedChunkReturnedWithMySqlFacts() {
        stubEmbed();
        when(vectorStore.search(any())).thenReturn(List.of(
                new VectorSearchResult(101L, 9L, 0, 0.9f))); // qdrant fileId=9 只是参考
        when(chunkRepository.findOwnedChunk(101L, 1L))
                .thenReturn(Optional.of(chunk(101L, 2, "真实内容", 5L))); // MySQL 权威 fileId=5

        List<RetrievalResult> results = service.search("q", alice);

        assertEquals(1, results.size());
        assertEquals(101L, results.get(0).chunkId());
        assertEquals(5L, results.get(0).fileId(), "fileId 应取 MySQL");
        assertEquals(2, results.get(0).chunkIndex(), "chunkIndex 应取 MySQL");
        assertEquals("真实内容", results.get(0).content());
        assertEquals(0.9f, results.get(0).score(), "score 应保留 Qdrant");
    }

    @Test
    @DisplayName("排名顺序保持：Qdrant 返回顺序即输出顺序")
    void preservesRankOrder() {
        stubEmbed();
        when(vectorStore.search(any())).thenReturn(List.of(
                new VectorSearchResult(101L, 1L, 0, 0.9f),
                new VectorSearchResult(202L, 1L, 3, 0.7f)));
        when(chunkRepository.findOwnedChunk(101L, 1L)).thenReturn(Optional.of(chunk(101L, 0, "A", 1L)));
        when(chunkRepository.findOwnedChunk(202L, 1L)).thenReturn(Optional.of(chunk(202L, 3, "B", 1L)));

        List<RetrievalResult> results = service.search("q", alice);

        assertEquals(List.of(101L, 202L), results.stream().map(RetrievalResult::chunkId).toList());
    }

    @Test
    @DisplayName("orphan chunk（Qdrant 有、MySQL 无）被跳过")
    void orphanChunkSkipped() {
        stubEmbed();
        when(vectorStore.search(any())).thenReturn(List.of(
                new VectorSearchResult(999L, 1L, 0, 0.9f),
                new VectorSearchResult(101L, 1L, 1, 0.8f)));
        when(chunkRepository.findOwnedChunk(999L, 1L)).thenReturn(Optional.empty());
        when(chunkRepository.findOwnedChunk(101L, 1L)).thenReturn(Optional.of(chunk(101L, 1, "存在", 1L)));

        List<RetrievalResult> results = service.search("q", alice);

        assertEquals(1, results.size());
        assertEquals(101L, results.get(0).chunkId());
    }

    @Test
    @DisplayName("越权：Qdrant 命中指向其他用户的真实 chunk → findOwnedChunk 为空 → 跳过")
    void otherUserChunkSkipped() {
        stubEmbed();
        when(vectorStore.search(any())).thenReturn(List.of(
                new VectorSearchResult(999L, 2L, 0, 0.9f))); // 命中 bob(2) 的 chunk
        // 当前用户 alice 查不到该 chunk（owner-aware）
        when(chunkRepository.findOwnedChunk(999L, 1L)).thenReturn(Optional.empty());

        List<RetrievalResult> results = service.search("q", alice);

        assertTrue(results.isEmpty());
        // 校验确实按当前 userId 查，而不是裸 findById
        verify(chunkRepository).findOwnedChunk(999L, 1L);
        verify(chunkRepository, never()).findById(999L);
    }
}
