package com.example.demo.service;

import com.example.demo.dto.ImageAnalysisResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Mock 多模态实现 —— 开发/测试用，不调用真实模型。
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockMultimodalAIService implements MultimodalAIService {

    @Override
    public ImageAnalysisResult analyzeImage(byte[] imageBytes, String mimeType, String prompt) {
        return new ImageAnalysisResult(
                "这是一张模拟分析的图片",
                List.of("物体A", "物体B"),
                "室内场景",
                "示例文字内容",
                List.of());
    }

    @Override
    public String getProvider() { return "mock"; }

    @Override
    public String getModelName() { return "mock-multimodal"; }
}
