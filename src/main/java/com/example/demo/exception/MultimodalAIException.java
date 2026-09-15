package com.example.demo.exception;

/**
 * 多模态模型调用异常 —— 统一包装 Provider SDK / 网络 / 结构化输出非法等错误，
 * 使业务层不依赖具体第三方异常。
 * <p>
 * 分析流程内部捕获后转 FAILED + failureReason，不直接抛到 HTTP。
 */
public class MultimodalAIException extends RuntimeException {

    public MultimodalAIException(String message) {
        super(message);
    }

    public MultimodalAIException(String message, Throwable cause) {
        super(message, cause);
    }
}
