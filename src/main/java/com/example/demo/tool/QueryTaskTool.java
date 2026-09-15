package com.example.demo.tool;

import com.example.demo.dto.TaskResponse;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.TaskNotFoundException;
import com.example.demo.model.User;
import com.example.demo.service.TaskService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool：queryTask —— 查询任务详情（只读）。
 * 权限：TaskService.getTask 内含归属校验（他人任务 → Forbidden）。
 */
@Component
public class QueryTaskTool implements Tool {

    private final TaskService taskService;

    public QueryTaskTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("queryTask", "查询当前用户的某个任务详情")
                .param("taskId", ToolDefinition.INTEGER, "任务 id（必填）", true)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, User user) {
        Long taskId;
        try {
            taskId = ToolArgs.toLong(arguments.get("taskId"));
        } catch (IllegalArgumentException e) {
            return ToolResult.error("queryTask", ToolErrorCode.INVALID_ARGUMENT, e.getMessage(), false);
        }
        try {
            TaskResponse task = taskService.getTask(taskId, user);
            return ToolResult.ok("queryTask", task);
        } catch (TaskNotFoundException e) {
            return ToolResult.error("queryTask", ToolErrorCode.NOT_FOUND, "任务不存在: id=" + taskId, false);
        } catch (ForbiddenException e) {
            return ToolResult.error("queryTask", ToolErrorCode.FORBIDDEN, "无权访问该任务", false);
        }
    }
}
