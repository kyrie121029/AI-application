package com.example.demo.dto;

/**
 * 向量检索命中（Qdrant 层结果，保持 score 排名顺序）。
 */
public record VectorSearchResult(
        Long chunkId,
        Long fileId,
        int chunkIndex,
        float score
) {
}
