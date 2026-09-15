package com.example.demo.exception;

/**
 * 图片分析状态冲突 —— PROCESSING/SUCCESS/PENDING 重复提交 → HTTP 409。
 */
public class ImageAnalysisConflictException extends RuntimeException {

    public ImageAnalysisConflictException(String message) {
        super(message);
    }
}
