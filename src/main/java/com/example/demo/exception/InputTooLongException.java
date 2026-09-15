package com.example.demo.exception;

/**
 * 当前用户消息超出 Token 上下文预算 → HTTP 413。
 * <p>
 * 不静默截断用户输入：system + 当前消息 + 预留输出 已超过 maxInputTokens 时抛出，
 * 由上层在构造模型请求前拦截。
 */
public class InputTooLongException extends RuntimeException {

    public InputTooLongException(String message) {
        super(message);
    }
}
