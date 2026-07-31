package com.example.demo.exception;

/**
 * AI 响应解析异常 —— 大模型返回的内容无法解析为预期结构时抛出
 * <p>
 * 区别于网络超时（应重试），这个异常不应重试——内容已经拿到了但格式不对。
 */
public class AIResponseParseException extends RuntimeException {

    private final String rawResponse;

    public AIResponseParseException(String message, String rawResponse) {
        super(message);
        this.rawResponse = rawResponse;
    }

    /** 返回原始响应的截断版本，避免日志过大 */
    public String getRawResponsePreview() {
        if (rawResponse == null) return "";
        return rawResponse.substring(0, Math.min(500, rawResponse.length()));
    }
}
