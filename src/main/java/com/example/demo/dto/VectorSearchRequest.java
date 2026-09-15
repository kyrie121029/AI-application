package com.example.demo.dto;

import java.util.List;

/**
 * 向量检索请求。
 * <p>
 * 权限在 Qdrant filter 阶段做（userId 必填），不允许先全局 Top-K 后到 Java 过滤。
 */
public record VectorSearchRequest(
        float[] queryVector,
        Long userId,
        List<Long> fileIds,
        int topK,
        Double scoreThreshold,
        String indexVersion
) {
}
