package com.example.demo.exception;

/**
 * AI 服务调用异常 —— 基类，所有 AI 相关异常继承此类
 * <p>
 * 区分四种子类型供上层决定是否重试：
 *   auth       → 401 认证失败 → 不重试
 *   rate_limit → 429 限流     → 指数退避重试
 *   timeout    → 超时         → 可重试
 *   server     → 5xx 服务端错  → 可重试
 *   network    → 网络不通     → 可重试
 */
public class AIServiceException extends RuntimeException {

    private final String errorType;

    public AIServiceException(String errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public AIServiceException(String errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    /** auth / rate_limit / timeout / server / network */
    public String getErrorType() {
        return errorType;
    }
}
