package com.example.demo.exception;

/**
 * 非法文件异常 —— 空文件、类型不允许、扩展名/MIME/真实内容不一致 → HTTP 400
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}
