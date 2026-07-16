package com.example.demo.service;

import com.example.demo.dto.CreateTaskRequest;
import com.example.demo.dto.CreateTaskResponse;
import com.example.demo.dto.MockResultResponse;
import com.example.demo.enums.TaskStatus;
import com.example.demo.exception.TaskNotFoundException;
import com.example.demo.model.Task;
import com.example.demo.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务服务 —— 业务逻辑层，负责任务的增删查改和模拟分析
 * <p>
 * 现已升级为 JPA 版本：用 TaskRepository 替代原来的 Map 存储，
 * 数据存入 H2 内存数据库，但操作方式跟 MySQL 完全一致。
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    // 注入 Repository，替代原来的 Map<Long, Task>
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * 创建任务
     * <p>
     * 对比旧版：taskStore.put(id, task) → taskRepository.save(task)
     * ID 不再需要手动管理，JPA 会自动生成。
     */
    public CreateTaskResponse createTask(CreateTaskRequest request) {
        // 构建 Task 对象（id 传 null，让 JPA 自动生成）
        Task task = new Task(null, request.getTitle(), request.getTaskType(), request.getInputText());

        // save = 数据库的 INSERT
        task = taskRepository.save(task);

        log.info("任务创建成功: id={}, title={}", task.getId(), task.getTitle());
        return new CreateTaskResponse(task.getId(), task.getStatus());
    }

    /**
     * 根据 ID 查询任务
     * <p>
     * 对比旧版：taskStore.get(id) → taskRepository.findById(id)
     */
    public Task getTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("任务不存在: id={}", id);
                    return new TaskNotFoundException(id);
                });
    }

    /**
     * 查询所有任务（按创建时间倒序）
     * <p>
     * 对比旧版：手动排序 → JPA 的 findAll 默认按插入顺序，
     * 这里用 List 反转实现倒序。
     */
    public List<Task> listTasks() {
        // new ArrayList 确保列表可修改（findAll 可能返回不可变列表）
        List<Task> tasks = new ArrayList<>(taskRepository.findAll());
        tasks.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return tasks;
    }

    /**
     * 根据 ID 删除任务
     * <p>
     * 对比旧版：taskStore.remove(id) → 先查存在再 taskRepository.deleteById(id)
     */
    public void deleteTask(Long id) {
        getTask(id); // 先确认存在
        taskRepository.deleteById(id);
        log.info("任务已删除: id={}", id);
    }

    /**
     * 为指定任务生成模拟分析结果
     */
    public MockResultResponse generateMockResult(Long id) {
        Task task = getTask(id);

        String summary = "这是对输入内容的模拟摘要：\"" + task.getInputText() + "\" 已完成初步分析。";
        String conclusion = "模拟分析结论：输入内容属于" + task.getTaskType() + "范畴，未发现明显异常。";
        String suggestion = "后续可以将该模拟逻辑替换为真实大模型 API 调用。";

        String resultJson = "{"
                + "\"summary\": \"" + summary + "\", "
                + "\"conclusion\": \"" + conclusion + "\", "
                + "\"suggestion\": \"" + suggestion + "\""
                + "}";
        task.setResult(resultJson);
        task.setStatus(TaskStatus.RESULT_GENERATED);
        task.setUpdatedAt(LocalDateTime.now());

        // save：如果 id 已存在就是 UPDATE，不存在才是 INSERT
        taskRepository.save(task);

        log.info("模拟结果已生成: id={}, status={}", id, TaskStatus.RESULT_GENERATED);

        MockResultResponse response = new MockResultResponse();
        response.setTaskId(id);
        response.setSummary(summary);
        response.setConclusion(conclusion);
        response.setSuggestion(suggestion);
        response.setStatus(task.getStatus());
        return response;
    }
}