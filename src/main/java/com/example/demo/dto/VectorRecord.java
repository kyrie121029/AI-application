package com.example.demo.dto;

/**
 * 向量存储记录 —— 业务层与 Vector Store 之间的数据载体。
 * <p>
 * 一个 DocumentChunk 对应一条 VectorRecord。
 */
public record VectorRecord(
        Long fileId,
        Long userId,
        Long chunkId,
        int chunkIndex,
        float[] vector,
        String model,
        int dimension,
        String indexVersion
) {
}