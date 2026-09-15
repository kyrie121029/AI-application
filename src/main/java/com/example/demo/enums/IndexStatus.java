package com.example.demo.enums;

/**
 * 文档索引状态 —— 与 FileParseStatus / ImageAnalysisStatus 独立。
 * PENDING → INDEXING → SUCCESS / FAILED（FAILED 可重试回到 INDEXING）
 */
public enum IndexStatus {
    PENDING,
    INDEXING,
    SUCCESS,
    FAILED
}
