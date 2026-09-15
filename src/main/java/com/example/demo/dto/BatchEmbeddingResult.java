package com.example.demo.dto;

import java.util.List;

/**
 * 批量 Embedding 结果。
 * <p>
 * embeddings 已按原始输入顺序排列（Provider 返回乱序 index 时已在 Provider 层恢复），
 * 因此 embeddings.get(i) 对应 inputs.get(i)。
 */
public record BatchEmbeddingResult(
        String provider,
        String model,
        int dimension,
        List<EmbeddingVector> embeddings,
        Integer inputTokens,
        int retryCount
) {

    /** 单个向量 + 其在批量输入中的 index */
    public record EmbeddingVector(int index, float[] vector) {
    }
}
