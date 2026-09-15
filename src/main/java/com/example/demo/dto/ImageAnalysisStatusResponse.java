package com.example.demo.dto;

import com.example.demo.enums.ImageAnalysisStatus;
import com.example.demo.model.ImageAnalysis;

import java.time.LocalDateTime;

/**
 * 图片分析状态响应。
 */
public class ImageAnalysisStatusResponse {

    private ImageAnalysisStatus status;
    private String failureReason;
    private LocalDateTime analyzedAt;

    private ImageAnalysisStatusResponse() {}

    public static ImageAnalysisStatusResponse from(ImageAnalysis a) {
        ImageAnalysisStatusResponse r = new ImageAnalysisStatusResponse();
        r.status = a.getStatus();
        r.failureReason = a.getFailureReason();
        r.analyzedAt = a.getAnalyzedAt();
        return r;
    }

    /** 未提交过分析时的空状态（PENDING） */
    public static ImageAnalysisStatusResponse pending() {
        ImageAnalysisStatusResponse r = new ImageAnalysisStatusResponse();
        r.status = ImageAnalysisStatus.PENDING;
        return r;
    }

    public ImageAnalysisStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
}
