package com.example.demo.dto;

import java.util.List;

/**
 * 多用例检索评估汇总。空 cases 时各均值为 0（不做除零）。
 */
public record RetrievalEvaluationSummary(
        int caseCount,
        double averageRecallAtK,
        double averagePrecisionAtK,
        double hitRateAtK,
        double mrr,
        List<RetrievalEvaluationResult> perCaseResults
) {
}
