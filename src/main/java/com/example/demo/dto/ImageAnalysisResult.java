package com.example.demo.dto;

import java.util.List;

/**
 * 图片分析结果（多模态模型结构化输出）。
 * textContent 只表示模型在图片中看到的文字内容，不是高可靠 OCR。
 */
public record ImageAnalysisResult(
        String summary,
        List<String> objects,
        String scene,
        String textContent,
        List<String> riskFlags
) {
}
