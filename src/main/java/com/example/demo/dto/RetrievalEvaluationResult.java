package com.example.demo.dto;

import java.util.List;

/**
 * 单个检索评估用例的指标结果。
 * <p>
 * retrievedChunkIds 为去重后保持首次命中 rank 顺序的列表（RR 用该顺序）。
 * 指标均按唯一 chunkId 计算。
 */
public record RetrievalEvaluationResult(
        String query,
        List<Long> retrievedChunkIds,
        List<Long> relevantChunkIds,
        double recallAtK,
        double precisionAtK,
        boolean hitAtK,
        double reciprocalRank
) {
}
