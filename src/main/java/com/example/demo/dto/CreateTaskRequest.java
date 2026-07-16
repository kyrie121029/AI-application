package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建任务请求 DTO —— 前端传过来的参数都在这
 * <p>
 * 字段上的注解（@NotBlank、@Size）会在 Controller 收到请求时自动校验，
 * 不通过则抛出 MethodArgumentNotValidException，由 GlobalExceptionHandler 统一处理。
 */
public class CreateTaskRequest {

    /** 任务标题 —— 不能为空 */
    @NotBlank(message = "标题不能为空")
    private String title;

    /** 任务类型 —— 不能为空 */
    @NotBlank(message = "任务类型不能为空")
    private String taskType;

    /** 用户输入的待分析文本 —— 不能为空，最长 500 字符 */
    @NotBlank(message = "输入文本不能为空")
    @Size(max = 500, message = "输入文本不能超过500个字符")
    private String inputText;

    // ==================== Getter / Setter ====================

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
    }
}