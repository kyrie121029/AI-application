package com.example.demo.exception;

/**
 * 解析状态冲突 —— PROCESSING 重复提交 / SUCCESS 重复解析 → HTTP 409。
 */
public class ParseConflictException extends RuntimeException {

    public ParseConflictException(String message) {
        super(message);
    }
}
