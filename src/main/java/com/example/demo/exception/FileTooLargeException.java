package com.example.demo.exception;

/**
 * 文件大小超过业务层上限 → HTTP 413
 * （与 Spring multipart 层的 MaxUploadSizeExceededException 使用同一状态码）
 */
public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(String message) {
        super(message);
    }
}
