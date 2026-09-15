package com.example.demo.dto;

/**
 * Chunk Embedding 草稿 —— 业务编排层把 Chunk 映射到向量的中间结果。
 * <p>
 * Phase 7.2 不持久化 Vector；此 DTO 仅用于证明 Chunk ↔ Vector 映射正确，
 * Phase 7.3 再决定 Vector Store 数据模型。
 */
public record ChunkEmbeddingDraft(
        Long chunkId,
        int chunkIndex,
        float[] vector,
        String model,
        int dimension
) {
}
