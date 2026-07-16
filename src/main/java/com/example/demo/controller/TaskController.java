package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.CreateTaskRequest;
import com.example.demo.dto.CreateTaskResponse;
import com.example.demo.dto.MockResultResponse;
import com.example.demo.model.Task;
import com.example.demo.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务控制器 —— 接收 HTTP 请求，调用 Service 处理，返回统一格式结果
 * <p>
 * 所有接口路径统一以 /api/tasks 开头
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    // 构造器注入（Spring 推荐的注入方式）
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 创建任务
     * <p>
     * 请求方式：POST /api/tasks
     */
    @PostMapping
    public ApiResponse<CreateTaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        CreateTaskResponse response = taskService.createTask(request);
        return ApiResponse.success(response);
    }

    /**
     * 根据 ID 查询单个任务
     * <p>
     * 请求方式：GET /api/tasks/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<Task> getTask(@PathVariable Long id) {
        Task task = taskService.getTask(id);
        return ApiResponse.success(task);
    }

    /**
     * 查询所有任务
     * <p>
     * 请求方式：GET /api/tasks
     */
    @GetMapping
    public ApiResponse<List<Task>> listTasks() {
        List<Task> tasks = taskService.listTasks();
        return ApiResponse.success(tasks);
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ApiResponse.success();
    }

    /**
     * 为任务生成模拟分析结果
     * <p>
     * 请求方式：POST /api/tasks/{id}/mock-result
     */
    @PostMapping("/{id}/mock-result")
    public ApiResponse<MockResultResponse> generateMockResult(@PathVariable Long id) {
        MockResultResponse response = taskService.generateMockResult(id);
        return ApiResponse.success(response);
    }
}
