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

    /** 关联的会话 ID（流式对话场景） */
    @Column(name = "conversation_id")
    private Long conversationId;

    /** 关联的文件 ID（图片分析 / 文件 Embedding 场景） */
    @Column(name = "file_id")
    private Long fileId;

    /** 调用类型：GENERATION / STREAMING / MULTIMODAL / EMBEDDING（旧数据为 null） */
    @Column(name = "call_type", length = 20)
    private String callType;

    /** 首 Token 延迟（毫秒），流式场景 */
    @Column(name = "first_token_latency_ms")
    private long firstTokenLatencyMs;

    /** 重试次数 */
    @Column(name = "retry_count")
    private int retryCount;

    /** 是否在收到首个 Token 前就失败 */
    @Column(name = "failed_before_first_token")
    private boolean failedBeforeFirstToken;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

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

    /** 流式成功调用 */
    public AIUsageLog(Long userId, Long conversationId, String provider, String modelName,
                      long elapsedMs, long firstTokenLatencyMs, int retryCount,
                      String promptVersion) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.success = true;
        this.provider = provider;
        this.modelName = modelName;
        this.elapsedMs = elapsedMs;
        this.firstTokenLatencyMs = firstTokenLatencyMs;
        this.retryCount = retryCount;
        this.promptVersion = promptVersion;
        this.createdAt = LocalDateTime.now();
    }

    /** 流式失败调用 */
    public AIUsageLog(Long userId, Long conversationId, String provider, String modelName,
                      long elapsedMs, long firstTokenLatencyMs, int retryCount,
                      boolean failedBeforeFirstToken, String errorType, String promptVersion) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.success = false;
        this.provider = provider;
        this.modelName = modelName;
        this.elapsedMs = elapsedMs;
        this.firstTokenLatencyMs = firstTokenLatencyMs;
        this.retryCount = retryCount;
        this.failedBeforeFirstToken = failedBeforeFirstToken;
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
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public String getCallType() { return callType; }
    public void setCallType(String callType) { this.callType = callType; }
    public long getFirstTokenLatencyMs() { return firstTokenLatencyMs; }
    public void setFirstTokenLatencyMs(long firstTokenLatencyMs) { this.firstTokenLatencyMs = firstTokenLatencyMs; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public boolean isFailedBeforeFirstToken() { return failedBeforeFirstToken; }
    public void setFailedBeforeFirstToken(boolean failedBeforeFirstToken) { this.failedBeforeFirstToken = failedBeforeFirstToken; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
