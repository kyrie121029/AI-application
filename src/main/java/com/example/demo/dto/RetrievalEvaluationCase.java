package com.example.demo.dto;

import java.util.List;

/**
 * 离线检索评估用例 —— 人工标注 Ground Truth。
 * <p>
 * 校验：query 非空；relevantChunkIds 非空且不含 null（构造时快速失败）。
 */
public record RetrievalEvaluationCase(
        String query,
        List<Long> fileIds,
        List<Long> relevantChunkIds
) {

    public RetrievalEvaluationCase {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (relevantChunkIds == null || relevantChunkIds.isEmpty()) {
            throw new IllegalArgumentException("relevantChunkIds 不能为空");
        }
        for (Long id : relevantChunkIds) {
            if (id == null) {
                throw new IllegalArgumentException("relevantChunkIds 不能包含 null");
            }
        }
    }
}
