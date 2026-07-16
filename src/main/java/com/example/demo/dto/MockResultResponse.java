package com.example.demo.dto;

import com.example.demo.enums.TaskStatus;

/**
 * 模拟结果响应 DTO —— 模拟 AI 分析后返回的结果
 */
public class MockResultResponse {

    /** 任务 ID */
    private Long taskId;

    /** 模拟摘要 */
    private String summary;

    /** 模拟分析结论 */
    private String conclusion;

    /** 模拟建议 */
    private String suggestion;

    /** 任务当前状态 */
    private TaskStatus status;

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
}
