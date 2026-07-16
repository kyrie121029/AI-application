package com.example.demo.dto;

import com.example.demo.enums.TaskStatus;

/**
 * 创建任务响应 DTO —— 创建完成后返回给前端的数据
 */
public class CreateTaskResponse {

    /** 新创建的任务 ID */
    private Long taskId;

    /** 任务当前状态 */
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
