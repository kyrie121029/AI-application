package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * AI 调用日志 —— 记录每次 AI 调用的详细信息
 */
@Entity
@Table(name = "ai_usage_logs")
public class AIUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "success")
    private boolean success;

    private String provider;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "input_tokens")
    private int inputTokens;

    @Column(name = "output_tokens")
    private int outputTokens;

    @Column(name = "elapsed_ms")
    private long elapsedMs;

    @Column(name = "request_preview", columnDefinition = "TEXT")
    private String requestPreview;

    /** 错误类型：auth / rate_limit / timeout / parse / server / null */
    @Column(name = "error_type")
    private String errorType;

    /** Prompt 模板版本号，如 task-analysis-v1 */
    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ==================== 构造 ====================

    public AIUsageLog() {}

    /** 成功调用 */
    public AIUsageLog(Long userId, Long taskId, String provider, String modelName,
                      int inputTokens, int outputTokens, long elapsedMs,
                      String requestPreview, String promptVersion) {
        this.userId = userId;
        this.taskId = taskId;
        this.success = true;
        this.provider = provider;
        this.modelName = modelName;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.elapsedMs = elapsedMs;
        this.requestPreview = requestPreview;
        this.promptVersion = promptVersion;
        this.createdAt = LocalDateTime.now();
    }

    /** 失败调用 */
    public AIUsageLog(Long userId, Long taskId, String provider, String modelName,
                      long elapsedMs, String requestPreview,
                      String errorType, String promptVersion) {
        this.userId = userId;
        this.taskId = taskId;
        this.success = false;
        this.provider = provider;
        this.modelName = modelName;
        this.elapsedMs = elapsedMs;
        this.requestPreview = requestPreview;
        this.errorType = errorType;
        this.promptVersion = promptVersion;
        this.createdAt = LocalDateTime.now();
    }

    // ==================== Getter / Setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }
    public String getRequestPreview() { return requestPreview; }
    public void setRequestPreview(String requestPreview) { this.requestPreview = requestPreview; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
