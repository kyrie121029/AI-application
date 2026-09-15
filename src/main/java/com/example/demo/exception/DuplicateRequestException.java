package com.example.demo.exception;

/**
 * 重复请求异常 —— 同一 requestId 已被处理过时抛出 → 409
 */
public class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException(String message) {
        super(message);
    }
}
