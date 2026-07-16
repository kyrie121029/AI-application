package com.example.demo.service;

import com.example.demo.dto.CreateTaskRequest;
import com.example.demo.dto.CreateTaskResponse;
import com.example.demo.dto.MockResultResponse;
import com.example.demo.enums.TaskStatus;
import com.example.demo.exception.TaskNotFoundException;
import com.example.demo.model.Task;
import com.example.demo.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TaskService 单元测试
 * <p>
 * 核心概念：
 *   @Mock  : 创建一个"假"的 TaskRepository，不会真的操作数据库
 *   @InjectMocks : 把假的 TaskRepository 注入到 TaskService 里
 *   when(...).thenReturn(...) : 设定假对象的行为（打桩）
 *   assertXxx : 验证结果是否符合预期
 *   verify    : 验证某个方法是否被调用过
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    // ==================== 测试前准备 ====================

    @Mock
    private TaskRepository taskRepository;   // 假的数据库

    @InjectMocks
    private TaskService taskService;         // 要测试的对象（注入假依赖）

    private CreateTaskRequest sampleRequest;

    @BeforeEach
    void setUp() {
        // 每个测试方法执行前，准备好一个样本请求
        sampleRequest = new CreateTaskRequest();
        sampleRequest.setTitle("测试标题");
        sampleRequest.setTaskType("文本分析");
        sampleRequest.setInputText("测试输入内容");
    }

    // ==================== 测试方法 ====================

    @Test
    @DisplayName("创建任务 → 应返回 taskId 和 PENDING 状态")
    void shouldCreateTask() {
        // 1. 设定假行为：调用 save 时返回什么
        Task savedTask = new Task(1L, "测试标题", "文本分析", "测试输入内容");
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        // 2. 执行被测试的方法
        CreateTaskResponse response = taskService.createTask(sampleRequest);

        // 3. 断言：结果必须符合预期
        assertNotNull(response, "返回值不能为空");
        assertEquals(1L, response.getTaskId(), "ID 应为 1");
        assertEquals(TaskStatus.PENDING, response.getStatus(), "状态应是 PENDING");

        // 4. 验证：save 被调用了 1 次
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    @DisplayName("查询存在的任务 → 应返回对应任务对象")
    void shouldGetTask() {
        // 准备一个假任务
        Task mockTask = new Task(1L, "测试", "类型", "输入");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(mockTask));

        // 执行
        Task result = taskService.getTask(1L);

        // 断言
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("测试", result.getTitle());
    }

    @Test
    @DisplayName("查询不存在的任务 → 应抛出 TaskNotFoundException")
    void shouldThrowWhenTaskNotFound() {
        // 设定：查不到就返回 Optional.empty()
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        // 断言：一定会抛异常
        assertThrows(TaskNotFoundException.class, () -> {
            taskService.getTask(999L);
        });
    }

    @Test
    @DisplayName("列出所有任务 → 应返回列表")
    void shouldListTasks() {
        Task t1 = new Task(1L, "标题1", "类型1", "输入1");
        Task t2 = new Task(2L, "标题2", "类型2", "输入2");
        when(taskRepository.findAll()).thenReturn(List.of(t1, t2));

        List<Task> tasks = taskService.listTasks();

        assertNotNull(tasks);
        assertEquals(2, tasks.size(), "应该有 2 条任务");
    }

    @Test
    @DisplayName("删除存在的任务 → 不应抛异常")
    void shouldDeleteTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(new Task()));
        doNothing().when(taskRepository).deleteById(1L);

        // 不抛异常就算通过
        assertDoesNotThrow(() -> taskService.deleteTask(1L));

        verify(taskRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("删除不存在的任务 → 应抛出 TaskNotFoundException")
    void shouldThrowWhenDeleteNotFound() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> {
            taskService.deleteTask(999L);
        });

        // 不应该调用 deleteById
        verify(taskRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("生成模拟结果 → 应更新状态为 RESULT_GENERATED")
    void shouldGenerateMockResult() {
        Task task = new Task(1L, "测试", "分析", "输入文本");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        MockResultResponse response = taskService.generateMockResult(1L);

        assertNotNull(response);
        assertEquals(1L, response.getTaskId());
        assertEquals(TaskStatus.RESULT_GENERATED, response.getStatus());
        // 摘要里应包含原始输入文本
        assertTrue(response.getSummary().contains("输入文本"), "摘要应引用输入文本");

        // 验证：save 被调用了（先查出来再存回去）
        verify(taskRepository, times(1)).save(any(Task.class));
    }
}