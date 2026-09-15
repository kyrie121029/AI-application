package com.example.demo.dto;

/**
 * 单文本 Embedding 结果。
 */
public record EmbeddingResult(
        String provider,
        String model,
        int dimension,
        float[] vector,
        Integer inputTokens,
        int retryCount
) {
}
