package com.example.demo.exception;

/**
 * Embedding 调用异常 —— 在 AIServiceException 基础上额外携带真实重试次数。
 * <p>
 * 供业务层记录 AIUsageLog.retryCount（否则最终失败时重试次数会丢失）。
 */
public class EmbeddingCallException extends AIServiceException {

    private final int retryCount;

    public EmbeddingCallException(String errorType, String message, int retryCount) {
        super(errorType, message);
        this.retryCount = retryCount;
    }

    public EmbeddingCallException(String errorType, String message, Throwable cause, int retryCount) {
        super(errorType, message, cause);
        this.retryCount = retryCount;
    }

    public int getRetryCount() { return retryCount; }
}
