package com.example.demo.dto;

/**
 * 本次请求内使用的一个 Context 来源。
 * sourceId 为本次请求内稳定的 S1/S2/... 标识（非全局持久 ID）。
 */
public record RagContextSource(
        String sourceId,
        Long fileId,
        Long chunkId,
        int chunkIndex,
        float score,
        String text
) {
}
