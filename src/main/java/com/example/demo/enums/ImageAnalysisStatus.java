package com.example.demo.enums;

/**
 * 图片分析状态机。
 * PENDING → PROCESSING → SUCCESS / FAILED（FAILED 可重试回到 PENDING）
 */
public enum ImageAnalysisStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED
}
