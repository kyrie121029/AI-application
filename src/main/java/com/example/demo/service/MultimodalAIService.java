package com.example.demo.service;

import com.example.demo.dto.ImageAnalysisResult;
import com.example.demo.exception.MultimodalAIException;

/**
 * 多模态 AI 服务抽象 —— 图片内容理解。
 * <p>
 * 与文本 AIService 分离（单一职责）；只接收已读入的图片字节，不负责存储与流管理。
 */
public interface MultimodalAIService {

    /**
     * 分析图片。
     *
     * @param imageBytes 图片字节（调用方已按大小限制读取）
     * @param mimeType   图片 MIME（如 image/jpeg）
     * @param prompt     System Prompt
     */
    ImageAnalysisResult analyzeImage(byte[] imageBytes, String mimeType, String prompt)
            throws MultimodalAIException;

    /** 当前 Provider 标识（用于日志） */
    String getProvider();

    /** 当前模型名（用于日志） */
    String getModelName();
}
