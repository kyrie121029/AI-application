package com.example.demo.service;

import com.example.demo.dto.ContextAssemblyResult;
import com.example.demo.dto.RetrievalResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextAssembler 单元测试 —— 真预算上限、跳过大 Chunk 继续、Source ID 按实际纳入分配。
 */
class ContextAssemblerTest {

    /** 每字符 1 token */
    private final TokenEstimator estimator = t -> t == null ? 0 : t.length();
    private final ContextAssembler assembler = new ContextAssembler(estimator);

    private RetrievalResult hit(Long chunkId, String content, float score) {
        return new RetrievalResult(1L, chunkId, 0, content, score);
    }

    @Test
    @DisplayName("按 rank + 预算选择，Source ID 按实际纳入顺序分配")
    void selectsInRankWithinBudget() {
        List<RetrievalResult> hits = List.of(
                hit(101L, "AAAAA", 0.9f),          // 9 token
                hit(102L, "BBBBBBBB", 0.8f),       // 12 token
                hit(103L, "CCCCCCCCCCCCCCC", 0.7f)); // 19 token，放不下
        ContextAssemblyResult r = assembler.assemble(hits, 30);

        assertEquals(2, r.sources().size());
        assertEquals("S1", r.sources().get(0).sourceId());
        assertEquals(101L, r.sources().get(0).chunkId());
        assertEquals("S2", r.sources().get(1).sourceId());
        assertTrue(r.truncated());
    }

    @Test
    @DisplayName("单个首 Chunk 超预算 → 不加入，返回空 sources + truncated")
    void singleOverBudgetHitNotIncluded() {
        ContextAssemblyResult r = assembler.assemble(List.of(hit(101L, "x".repeat(100), 0.9f)), 20);
        assertEquals(0, r.sources().size());
        assertTrue(r.truncated());
        assertEquals(0, r.estimatedTokens());
    }

    @Test
    @DisplayName("首 Chunk 超预算、后续小 Chunk 可放 → 后续成为 S1")
    void skipsFirstThenIncludesLater() {
        List<RetrievalResult> hits = List.of(
                hit(101L, "x".repeat(100), 0.9f), // 超预算
                hit(102L, "BBB", 0.8f));          // 7 token
        ContextAssemblyResult r = assembler.assemble(hits, 20);

        assertEquals(1, r.sources().size());
        assertEquals("S1", r.sources().get(0).sourceId());
        assertEquals(102L, r.sources().get(0).chunkId());
        assertTrue(r.truncated());
        assertTrue(r.estimatedTokens() <= 20);
    }

    @Test
    @DisplayName("中间 Chunk 放不下、后续可放 → 继续选择")
    void skipsMiddleKeepsLater() {
        List<RetrievalResult> hits = List.of(
                hit(101L, "AAAAA", 0.9f),          // 9
                hit(102L, "x".repeat(100), 0.8f),  // 超预算，跳过
                hit(103L, "CCC", 0.7f));           // 7
        ContextAssemblyResult r = assembler.assemble(hits, 30);

        assertEquals(2, r.sources().size());
        assertEquals(101L, r.sources().get(0).chunkId());
        assertEquals(103L, r.sources().get(1).chunkId());
        assertEquals("S2", r.sources().get(1).sourceId());
        assertTrue(r.truncated());
    }

    @Test
    @DisplayName("恒不超预算：estimatedTokens <= maxContextTokens")
    void neverExceedsBudget() {
        List<RetrievalResult> hits = List.of(
                hit(101L, "a".repeat(50), 0.9f),
                hit(102L, "b".repeat(50), 0.8f),
                hit(103L, "c".repeat(50), 0.7f),
                hit(104L, "d".repeat(50), 0.6f));
        for (int budget = 5; budget <= 300; budget += 17) {
            ContextAssemblyResult r = assembler.assemble(hits, budget);
            assertTrue(r.estimatedTokens() <= budget,
                    "budget=" + budget + " est=" + r.estimatedTokens());
        }
    }

    @Test
    @DisplayName("Chunk 不被 substring：内容原样进入 context")
    void chunkNotSubstringed() {
        ContextAssemblyResult r = assembler.assemble(
                List.of(hit(101L, "完整的一段很长内容绝不截断", 0.9f)), 100);
        assertTrue(r.context().contains("完整的一段很长内容绝不截断"));
    }

    @Test
    @DisplayName("空检索 → 空 context、无来源、不 truncated")
    void emptyInput() {
        ContextAssemblyResult r = assembler.assemble(List.of(), 100);
        assertEquals(0, r.sources().size());
        assertFalse(r.truncated());
    }
}
