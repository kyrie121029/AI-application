package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "创建任务请求")
public class CreateTaskRequest {

    @NotBlank(message = "标题不能为空")
    @Schema(description = "任务标题", example = "文本分析")
    private String title;

    @NotBlank(message = "任务类型不能为空")
    @Schema(description = "任务类型", example = "文本分析")
    private String taskType;

    @NotBlank(message = "输入文本不能为空")
    @Size(max = 500, message = "输入文本不能超过500个字符")
    @Schema(description = "待分析的输入文本", example = "今天天气很好")
    private String inputText;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = inputText; }
}
