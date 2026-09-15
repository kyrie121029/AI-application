package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.model.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 离线检索评估 —— 复用 RetrievalService.search（不直接调 Embedding/VectorStore）。
 * <p>
 * 指标（均按唯一 chunkId）：
 *   Recall@K   = Top-K 命中 relevant 数 / relevant 总数
 *   Precision@K = Top-K 命中 relevant 数 / 实际去重 retrieved 数
 *   Hit@K      = 是否至少命中一个 relevant
 *   Reciprocal Rank = 第一个 relevant 的 rank 倒数（去重后顺序）；未命中 0
 * <p>
 * 不修改生产 Retrieval 逻辑。
 */
@Service
public class RetrievalEvaluationService {

    private final RetrievalService retrievalService;

    public RetrievalEvaluationService(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    /** 评估单个用例 */
    public RetrievalEvaluationResult evaluateCase(RetrievalEvaluationCase evalCase, User user) {
        List<RetrievalResult> results =
                retrievalService.search(evalCase.query(), user, evalCase.fileIds());

        // 去重并保持首次命中 rank 顺序（用于 RR）
        List<Long> retrieved = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (RetrievalResult r : results) {
            if (seen.add(r.chunkId())) {
                retrieved.add(r.chunkId());
            }
        }

        Set<Long> relevant = new LinkedHashSet<>(evalCase.relevantChunkIds());

        int hits = 0;
        int firstRelevantRank = -1; // 0-based（去重后顺序）
        for (int i = 0; i < retrieved.size(); i++) {
            if (relevant.contains(retrieved.get(i))) {
                if (hits == 0) firstRelevantRank = i;
                hits++;
            }
        }

        double recall = relevant.isEmpty() ? 0.0 : (double) hits / relevant.size();
        double precision = retrieved.isEmpty() ? 0.0 : (double) hits / retrieved.size();
        boolean hit = hits > 0;
        double rr = firstRelevantRank >= 0 ? 1.0 / (firstRelevantRank + 1) : 0.0;

        return new RetrievalEvaluationResult(evalCase.query(), retrieved,
                List.copyOf(evalCase.relevantChunkIds()), recall, precision, hit, rr);
    }

    /** 评估多个用例并汇总；空列表返回 caseCount=0、均值 0 的空 Summary */
    public RetrievalEvaluationSummary evaluateAll(List<RetrievalEvaluationCase> cases, User user) {
        if (cases == null || cases.isEmpty()) {
            return new RetrievalEvaluationSummary(0, 0.0, 0.0, 0.0, 0.0, List.of());
        }
        List<RetrievalEvaluationResult> perCase = new ArrayList<>(cases.size());
        double recallSum = 0, precisionSum = 0, hitSum = 0, rrSum = 0;
        for (RetrievalEvaluationCase c : cases) {
            RetrievalEvaluationResult r = evaluateCase(c, user);
            perCase.add(r);
            recallSum += r.recallAtK();
            precisionSum += r.precisionAtK();
            hitSum += r.hitAtK() ? 1 : 0;
            rrSum += r.reciprocalRank();
        }
        int n = cases.size();
        return new RetrievalEvaluationSummary(n,
                recallSum / n, precisionSum / n, hitSum / n, rrSum / n, perCase);
    }
}
