package com.example.demo.dto;

import com.example.demo.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建任务响应")
public class CreateTaskResponse {

    @Schema(description = "新创建的任务ID", example = "1")
    private Long taskId;

    @Schema(description = "任务状态", example = "PENDING")
    private TaskStatus status;

    // ==================== 构造方法 ====================

    public CreateTaskResponse() {
    }

    public CreateTaskResponse(Long taskId, TaskStatus status) {
        this.taskId = taskId;
        this.status = status;
    }

    // ==================== Getter / Setter ====================

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }
}
