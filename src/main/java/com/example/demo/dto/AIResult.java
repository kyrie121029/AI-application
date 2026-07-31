package com.example.demo.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 分析结果 —— AIService 的统一返回类型
 */
public class AIResult {

    @NotBlank(message = "AI 摘要不能为空")
    private String summary;

    @NotBlank(message = "AI 结论不能为空")
    private String conclusion;

    @NotBlank(message = "AI 建议不能为空")
    private String suggestion;

    /** 消耗的输入 Token 数 */
    private int inputTokens;

    /** 消耗的输出 Token 数 */
    private int outputTokens;

    /** 使用的模型名称 */
    private String modelName;

    // ==================== 构造 ====================

    public AIResult() {}

    public AIResult(String summary, String conclusion, String suggestion,
                    int inputTokens, int outputTokens, String modelName) {
        this.summary = summary;
        this.conclusion = conclusion;
        this.suggestion = suggestion;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.modelName = modelName;
    }

    // ==================== Getter / Setter ====================

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    /** 总 Token = 输入 + 输出 */
    public int getTokensUsed() {
        return inputTokens + outputTokens;
    }

    /** 用 ObjectMapper 序列化为 JSON（避免手拼字符串破坏结构） */
    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("summary", summary);
        map.put("conclusion", conclusion);
        map.put("suggestion", suggestion);
        map.put("model", modelName);
        map.put("inputTokens", inputTokens);
        map.put("outputTokens", outputTokens);
        try {
            return new ObjectMapper().writeValueAsString(map);
        } catch (JsonProcessingException e) {
            // fallback：理论上不会发生（Map 一定可序列化）
            return "{}";
        }
    }
}
