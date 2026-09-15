package com.example.demo.dto;

/**
 * RAG 引用 —— 只允许指向本次 Context 中真实存在的 Source。
 */
public record RagCitation(
        String sourceId,
        Long fileId,
        Long chunkId,
        int chunkIndex,
        float score
) {
}
