package com.example.demo.dto;

/**
 * 检索结果 —— 命中 Chunk 的完整业务视图（含内容）。
 */
public record RetrievalResult(
        Long fileId,
        Long chunkId,
        int chunkIndex,
        String content,
        float score
) {
}
