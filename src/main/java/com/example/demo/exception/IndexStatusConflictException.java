package com.example.demo.exception;

/**
 * 文档索引状态冲突 —— 另一请求正在 INDEXING 时重复提交 → 409。
 */
public class IndexStatusConflictException extends RuntimeException {

    public IndexStatusConflictException(String message) {
        super(message);
    }
}
