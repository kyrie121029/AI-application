package com.example.demo.exception;

/**
 * 文档解析异常 —— 统一包装 PDFBox / POI / 编码 / 存储读取等底层异常，
 * 使业务层不依赖具体第三方异常类型。
 * <p>
 * 解析流程内部捕获后转 FAILED + failureReason，不直接抛到 HTTP。
 */
public class DocumentParseException extends RuntimeException {

    public DocumentParseException(String message) {
        super(message);
    }

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
