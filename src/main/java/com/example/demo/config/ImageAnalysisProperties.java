package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图片分析配置 —— 绑定 application.yml 中 image-analysis.* 下的所有配置。
 */
@Component
@ConfigurationProperties(prefix = "image-analysis")
public class ImageAnalysisProperties {

    /** 是否启用图片分析（false 时上传图片后不自动提交） */
    private boolean enabled = true;

    /** 多模态模型名（通过环境变量注入，不硬编码） */
    private String model = "";

    /** 模型最大输出 token */
    private int maxTokens = 1000;

    /** 图片分析 System Prompt */
    private String prompt = "你是一个图片理解助手。请分析图片内容，严格用 JSON 格式输出，包含以下字段：" +
            "summary（一句话总结）、objects（图中物体列表）、scene（场景描述）、" +
            "textContent（图中可见文字，看不清可为空字符串）、riskFlags（风险标记列表，无可为空数组）。";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
}
