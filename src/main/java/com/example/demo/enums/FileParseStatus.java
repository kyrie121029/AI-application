package com.example.demo.enums;

/**
 * 文件解析状态机。
 * PENDING → PROCESSING → SUCCESS / FAILED（FAILED 可重试回到 PENDING）
 */
public enum FileParseStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED
}
