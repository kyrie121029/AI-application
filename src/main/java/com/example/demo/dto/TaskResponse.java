package com.example.demo.dto;

import com.example.demo.enums.TaskStatus;
import com.example.demo.model.Task;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 任务响应 DTO —— 对前端暴露的安全数据视图
 * <p>
 * 为什么不用 Task Entity 直接返回？
 * 1. Task 包含 User 对象 → 可能泄漏用户数据
 * 2. Entity 字段变更会影响 API 契约
 * 3. Swagger 文档不应暴露 JPA 内部结构（如 @JsonIgnoreProperties）
 */
@Schema(description = "任务响应")
public class TaskResponse {

    @Schema(description = "任务ID", example = "1")
    private Long id;

    @Schema(description = "任务标题", example = "文本分析")
    private String title;

    @Schema(description = "任务类型", example = "文本分析")
    private String taskType;

    @Schema(description = "待分析的输入文本", example = "今天天气很好")
    private String inputText;

    @Schema(description = "任务状态", example = "PENDING")
    private TaskStatus status;

    @Schema(description = "AI 分析结果（仅 RESULT_GENERATED 时有值）")
    private String result;

    @Schema(description = "所属用户ID")
    private Long userId;

    @Schema(description = "所属用户名")
    private String username;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "最后更新时间")
    private LocalDateTime updatedAt;

    // ==================== 构造方法 ====================

    private TaskResponse() {}

    /**
     * 从 Task Entity 安全转换为 TaskResponse
     * 只提取前端需要的字段，不暴露 User 对象内部细节
     */
    public static TaskResponse from(Task task) {
        TaskResponse r = new TaskResponse();
        r.id = task.getId();
        r.title = task.getTitle();
        r.taskType = task.getTaskType();
        r.inputText = task.getInputText();
        r.status = task.getStatus();
        r.result = task.getResult();
        r.createdAt = task.getCreatedAt();
        r.updatedAt = task.getUpdatedAt();
        // 安全提取用户信息：只取 id 和 username，不暴露 password
        if (task.getUser() != null) {
            r.userId = task.getUser().getId();
            r.username = task.getUser().getUsername();
        }
        return r;
    }

    // ==================== Getter（只读） ====================

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getTaskType() { return taskType; }
    public String getInputText() { return inputText; }
    public TaskStatus getStatus() { return status; }
    public String getResult() { return result; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
