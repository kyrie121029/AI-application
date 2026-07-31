package com.example.demo.dto;

import com.example.demo.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "模拟分析结果")
public class MockResultResponse {

    @Schema(description = "任务ID", example = "1")
    private Long taskId;

    @Schema(description = "模拟摘要")
    private String summary;

    @Schema(description = "模拟分析结论")
    private String conclusion;

    @Schema(description = "后续建议")
    private String suggestion;

    @Schema(description = "任务状态", example = "RESULT_GENERATED")
    private TaskStatus status;

    @Schema(description = "使用的模型名称", example = "mock")
    private String modelName;

    @Schema(description = "消耗的 Token 数", example = "0")
    private int tokensUsed;

    // ==================== Getter / Setter ====================

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public int getTokensUsed() { return tokensUsed; }
    public void setTokensUsed(int tokensUsed) { this.tokensUsed = tokensUsed; }
}
