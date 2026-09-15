package com.example.demo.service;

import com.example.demo.dto.RetrievalEvaluationCase;
import com.example.demo.dto.RetrievalEvaluationResult;
import com.example.demo.dto.RetrievalEvaluationSummary;
import com.example.demo.dto.RetrievalResult;
import com.example.demo.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RetrievalEvaluationService 单测 —— Recall/Precision/Hit/RR、去重、空 retrieval、Summary。
 */
@ExtendWith(MockitoExtension.class)
class RetrievalEvaluationServiceTest {

    @Mock private RetrievalService retrievalService;
    private RetrievalEvaluationService service;
    private final User alice = new User(1L, "alice", "pwd");

    @BeforeEach
    void setUp() {
        service = new RetrievalEvaluationService(retrievalService);
    }

    private RetrievalResult hit(Long chunkId) {
        return new RetrievalResult(1L, chunkId, 0, "c", 0.9f);
    }

    private RetrievalEvaluationCase aCase(String query, Long... relevant) {
        return new RetrievalEvaluationCase(query, null, List.of(relevant));
    }

    @Test
    @DisplayName("TopK 全命中 → recall=1 precision=1 hit RR=1")
    void fullHit() {
        when(retrievalService.search(eq("q"), eq(alice), eq(null)))
                .thenReturn(List.of(hit(101L), hit(102L)));

        RetrievalEvaluationResult r = service.evaluateCase(aCase("q", 101L, 102L), alice);

        assertEquals(1.0, r.recallAtK());
        assertEquals(1.0, r.precisionAtK());
        assertTrue(r.hitAtK());
        assertEquals(1.0, r.reciprocalRank());
    }

    @Test
    @DisplayName("部分命中 → recall 与 precision 正确")
    void partialHit() {
        when(retrievalService.search(eq("q"), eq(alice), eq(null)))
                .thenReturn(List.of(hit(999L), hit(201L), hit(301L)));

        RetrievalEvaluationResult r = service.evaluateCase(aCase("q", 201L, 301L), alice);

        assertEquals(1.0, r.recallAtK());      // 2/2
        assertEquals(2.0 / 3.0, r.precisionAtK()); // 2/3
        assertTrue(r.hitAtK());
        assertEquals(0.5, r.reciprocalRank()); // 第一个 relevant 201 在 rank2 → 1/2
    }

    @Test
    @DisplayName("完全未命中 → recall=0 precision=0 hit=false RR=0")
    void noHit() {
        when(retrievalService.search(eq("q"), eq(alice), eq(null)))
                .thenReturn(List.of(hit(999L), hit(888L)));

        RetrievalEvaluationResult r = service.evaluateCase(aCase("q", 201L), alice);

        assertEquals(0.0, r.recallAtK());
        assertEquals(0.0, r.precisionAtK());
        assertFalse(r.hitAtK());
        assertEquals(0.0, r.reciprocalRank());
    }

    @Test
    @DisplayName("第一名命中 → RR=1")
    void firstRankHitGivesRR1() {
        when(retrievalService.search(eq("q"), eq(alice), eq(null)))
                .thenReturn(List.of(hit(101L), hit(999L)));

        RetrievalEvaluationResult r = service.evaluateCase(aCase("q", 101L), alice);

        assertEquals(1.0, r.reciprocalRank());
    }

    @Test
    @DisplayName("第三名首次命中 → RR=1/3")
    void thirdRankFirstHitGivesRR13() {
        when(retrievalService.search(eq("q"), eq(alice), eq(null)))
                .thenReturn(List.of(hit(999L), hit(888L), hit(777L)));

        RetrievalEvaluationResult r = service.evaluateCase(aCase("q", 777L), alice);

        assertEquals(1.0 / 3.0, r.reciprocalRank());
    }

    @Test
    @DisplayName("Retrieval 返回空 → 各指标 0")
    void emptyRetrieval() {
        when(retrievalService.search(eq("q"), eq(alice), eq(null))).thenReturn(List.of());

        RetrievalEvaluationResult r = service.evaluateCase(aCase("q", 101L), alice);

        assertEquals(0.0, r.recallAtK());
        assertEquals(0.0, r.precisionAtK());
        assertFalse(r.hitAtK());
        assertEquals(0.0, r.reciprocalRank());
        assertTrue(r.retrievedChunkIds().isEmpty());
    }

    @Test
    @DisplayName("retrieved 重复 chunkId 不重复计数（precision/RR 用去重后顺序）")
    void duplicateRetrievedNotDoubleCounted() {
        when(retrievalService.search(eq("q"), eq(alice), eq(null)))
                .thenReturn(List.of(hit(101L), hit(101L), hit(102L))); // 101 重复

        RetrievalEvaluationResult r = service.evaluateCase(aCase("q", 101L), alice);

        assertEquals(List.of(101L, 102L), r.retrievedChunkIds()); // 去重保序
        assertEquals(1.0, r.recallAtK());                          // 1/1
        assertEquals(0.5, r.precisionAtK());                       // 1/2（唯一 retrieved 2）
        assertEquals(1.0, r.reciprocalRank());                     // 101 首次在 rank1
    }

    @Test
    @DisplayName("relevantChunkIds 非法（空 / null 元素 / 空 query）→ 构造失败")
    void invalidCaseFails() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetrievalEvaluationCase("q", null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new RetrievalEvaluationCase("q", null, java.util.Arrays.asList(1L, null)));
        assertThrows(IllegalArgumentException.class,
                () -> new RetrievalEvaluationCase("  ", null, List.of(1L)));
    }

    @Test
    @DisplayName("fileIds 正确传给 RetrievalService")
    void fileIdsPassedToRetrieval() {
        when(retrievalService.search(eq("q"), eq(alice), eq(List.of(7L))))
                .thenReturn(List.of(hit(101L)));

        RetrievalEvaluationCase c = new RetrievalEvaluationCase("q", List.of(7L), List.of(101L));
        service.evaluateCase(c, alice);

        verify(retrievalService).search("q", alice, List.of(7L));
    }

    @Test
    @DisplayName("多 case Summary：平均值正确")
    void summaryAverages() {
        // case1：recall=1/2 precision=1 hit RR=1/2（[999,201] relevant {201,301} → recall 0.5）
        when(retrievalService.search(eq("q1"), eq(alice), eq(null)))
                .thenReturn(List.of(hit(999L), hit(201L)));
        // case2：recall=1 precision=1 hit RR=1（[301] relevant {301}）
        when(retrievalService.search(eq("q2"), eq(alice), eq(null)))
                .thenReturn(List.of(hit(301L)));

        RetrievalEvaluationSummary s = service.evaluateAll(List.of(
                aCase("q1", 201L, 301L), aCase("q2", 301L)), alice);

        assertEquals(2, s.caseCount());
        assertEquals(0.75, s.averageRecallAtK());    // (0.5 + 1)/2
        assertEquals(0.75, s.averagePrecisionAtK()); // (0.5 + 1)/2
        assertEquals(1.0, s.hitRateAtK());
        assertEquals(0.75, s.mrr());                  // (0.5 + 1)/2
        assertEquals(2, s.perCaseResults().size());
    }

    @Test
    @DisplayName("空 cases → caseCount=0、均值 0，无除零")
    void emptyCasesSummary() {
        RetrievalEvaluationSummary s = service.evaluateAll(List.of(), alice);
        assertEquals(0, s.caseCount());
        assertEquals(0.0, s.averageRecallAtK());
        assertEquals(0.0, s.mrr());
        assertTrue(s.perCaseResults().isEmpty());
    }
}
