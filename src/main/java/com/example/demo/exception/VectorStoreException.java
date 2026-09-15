package com.example.demo.exception;

/**
 * 向量存储异常 —— 统一包装 Qdrant HTTP 调用 / 维度校验失败等。
 */
public class VectorStoreException extends RuntimeException {

    public VectorStoreException(String message) {
        super(message);
    }

    public VectorStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}