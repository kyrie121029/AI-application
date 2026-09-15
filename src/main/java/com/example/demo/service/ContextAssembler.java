package com.example.demo.service;

import com.example.demo.dto.ContextAssemblyResult;
import com.example.demo.dto.RagContextSource;
import com.example.demo.dto.RetrievalResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Context Assembly —— 按 rank 顺序选择完整 Chunk（不 substring），maxContextTokens 是**真上限**：
 * estimatedTokens 恒 <= maxContextTokens。放不下的 Chunk 跳过并继续尝试后续候选；
 * 只有实际加入的 Chunk 才分配本次请求内稳定的 S1/S2/...；若全部放不下 → 空 sources + truncated=true。
 */
@Component
public class ContextAssembler {

    /** Source 标记每份的固定开销（[S1]\n + 分隔），计入预算 */
    private static final int SOURCE_OVERHEAD_TOKENS = 4;

    private final TokenEstimator tokenEstimator;

    public ContextAssembler(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    public ContextAssemblyResult assemble(List<RetrievalResult> results, int maxContextTokens) {
        List<RagContextSource> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        int usedTokens = 0;
        boolean truncated = false;

        for (RetrievalResult r : results) {
            int chunkTokens = tokenEstimator.estimateTokens(r.content());
            int cost = chunkTokens + SOURCE_OVERHEAD_TOKENS;

            if (usedTokens + cost > maxContextTokens) {
                truncated = true; // 放不下：跳过，继续尝试后续候选；绝不超预算 / 不 substring
                continue;
            }

            String sourceId = "S" + (sources.size() + 1);
            sources.add(new RagContextSource(sourceId, r.fileId(), r.chunkId(),
                    r.chunkIndex(), r.score(), r.content()));
            if (sources.size() > 1) context.append("\n\n");
            context.append("[").append(sourceId).append("]\n").append(r.content());
            usedTokens += cost;
        }

        // truncated：曾跳过放不下的候选
        return new ContextAssemblyResult(context.toString(), sources, usedTokens, truncated);
    }
}
