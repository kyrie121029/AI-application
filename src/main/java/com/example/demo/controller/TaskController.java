package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.CurrentUser;
import com.example.demo.common.PageResponse;
import com.example.demo.dto.*;
import com.example.demo.model.User;
import com.example.demo.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "任务管理", description = "任务的增删查改和模拟分析")
@SecurityRequirement(name = "BearerAuth")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @Operation(summary = "创建任务")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "创建成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数校验失败"),
    })
    @PostMapping
    public ApiResponse<CreateTaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            @Parameter(hidden = true) @CurrentUser User user) {
        return ApiResponse.success(taskService.createTask(request, user));
    }

    @Operation(summary = "查询单个任务")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "任务不存在"),
    })
    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> getTask(
            @Parameter(description = "任务ID") @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        return ApiResponse.success(taskService.getTask(id, user));
    }

    @Operation(summary = "分页查询任务",
            description = "支持按状态/类型/关键字搜索。未登录时只返回当前用户的任务。")
    @GetMapping
    public ApiResponse<PageResponse<TaskResponse>> listTasks(
            @Parameter(description = "页码（从0开始）")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "排序字段")
            @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "按状态筛选：PENDING | RESULT_GENERATED")
            @RequestParam(required = false) String status,
            @Parameter(description = "按任务类型筛选")
            @RequestParam(required = false) String taskType,
            @Parameter(description = "关键字搜索（标题+输入文本）")
            @RequestParam(required = false) String keyword,
            @Parameter(hidden = true) @CurrentUser User user) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(sort).descending());
        return ApiResponse.success(
                PageResponse.of(taskService.listTasks(status, taskType, keyword, user, pageable)));
    }

    @Operation(summary = "删除任务")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权删除"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "任务不存在"),
    })
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask(
            @Parameter(description = "任务ID") @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        taskService.deleteTask(id, user);
        return ApiResponse.success();
    }

    @Operation(summary = "生成 AI 分析结果", description = "调用 AI 服务（Mock 或真实模型，取决于 ai.provider 配置）")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "生成成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权操作"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "任务不存在"),
    })
    @PostMapping("/{id}/generate")
    public ApiResponse<MockResultResponse> generateMockResult(
            @Parameter(description = "任务ID") @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        return ApiResponse.success(taskService.generateResult(id, user));
    }
}
