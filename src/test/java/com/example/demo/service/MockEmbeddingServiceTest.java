package com.example.demo.service;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.dto.BatchEmbeddingResult;
import com.example.demo.dto.EmbeddingResult;
import com.example.demo.exception.AIServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockEmbeddingService 单元测试 —— 确定性、维度、single 复用 batch、映射、空输入拒绝。
 */
class MockEmbeddingServiceTest {

    private MockEmbeddingService service;

    @BeforeEach
    void setUp() {
        EmbeddingProperties props = new EmbeddingProperties();
        props.setDimension(8);
        props.setBatchSize(32);
        service = new MockEmbeddingService(props);
    }

    @Test
    @DisplayName("同一输入产生相同向量（确定性，不依赖随机数）")
    void deterministicVector() {
        float[] v1 = service.embed("hello").vector();
        float[] v2 = service.embed("hello").vector();
        assertArrayEquals(v1, v2);
    }

    @Test
    @DisplayName("维度正确")
    void dimensionCorrect() {
        assertEquals(8, service.embed("hello").dimension());
        assertEquals(8, service.embed("hello").vector().length);
    }

    @Test
    @DisplayName("single 复用 batch，返回 1 条向量")
    void singleReusesBatch() {
        EmbeddingResult r = service.embed("hello");
        assertEquals("mock", r.provider());
        assertEquals("mock-embedding", r.model());
        assertNotNull(r.vector());
    }

    @Test
    @DisplayName("batch 的 index 与输入严格对应")
    void batchIndexMapsInput() {
        BatchEmbeddingResult r = service.embedBatch(List.of("a", "b", "c"));
        assertEquals(3, r.embeddings().size());
        assertEquals(0, r.embeddings().get(0).index());
        assertEquals(1, r.embeddings().get(1).index());
        assertEquals(2, r.embeddings().get(2).index());
    }

    @Test
    @DisplayName("空输入被拒绝，不发请求")
    void rejectsEmptyInput() {
        assertThrows(AIServiceException.class, () -> service.embed(null));
        assertThrows(AIServiceException.class, () -> service.embed(""));
        assertThrows(AIServiceException.class, () -> service.embedBatch(List.of()));
        assertThrows(AIServiceException.class, () -> service.embedBatch(java.util.Arrays.asList("a", null)));
        assertThrows(AIServiceException.class, () -> service.embedBatch(List.of("a", "")));
    }
}
