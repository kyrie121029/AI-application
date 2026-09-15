package com.example.demo.service;

import com.example.demo.dto.AIResult;
import com.example.demo.dto.CreateTaskRequest;
import com.example.demo.dto.CreateTaskResponse;
import com.example.demo.dto.MockResultResponse;
import com.example.demo.dto.TaskResponse;
import com.example.demo.enums.TaskStatus;
import com.example.demo.exception.AIServiceException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.TaskNotFoundException;
import com.example.demo.model.Task;
import com.example.demo.model.User;
import com.example.demo.config.PromptTemplate;
import com.example.demo.repository.AIUsageLogRepository;
import com.example.demo.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private AIService aiService;

    @Mock
    private AIUsageLogRepository usageLogRepository;

    @InjectMocks
    private TaskService taskService;

    private CreateTaskRequest sampleRequest;
    private User testUser;

    @BeforeEach
    void setUp() {
        sampleRequest = new CreateTaskRequest();
        sampleRequest.setTitle("测试标题");
        sampleRequest.setTaskType("文本分析");
        sampleRequest.setInputText("测试输入内容");

        testUser = new User(1L, "testuser", "encodedPassword");
    }

    @Test
    @DisplayName("创建任务")
    void shouldCreateTask() {
        Task savedTask = new Task(1L, "测试标题", "文本分析", "测试输入内容");
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        CreateTaskResponse response = taskService.createTask(sampleRequest, testUser);

        assertNotNull(response);
        assertEquals(1L, response.getTaskId());
        assertEquals(TaskStatus.PENDING, response.getStatus());
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    @DisplayName("查询自己的任务")
    void shouldGetOwnTask() {
        Task task = new Task(1L, "测试", "类型", "输入");
        task.setUser(testUser);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskResponse result = taskService.getTask(1L, testUser);
        assertNotNull(result);
        assertEquals("测试", result.getTitle());
        assertEquals("testuser", result.getUsername());
    }

    @Test
    @DisplayName("查询别人的任务 → 应抛 ForbiddenException")
    void shouldRejectOtherUsersTask() {
        User otherUser = new User(999L, "other", "pwd");
        Task task = new Task(1L, "测试", "类型", "输入");
        task.setUser(otherUser);   // 任务属于 otherUser，不是 testUser
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThrows(ForbiddenException.class, () -> taskService.getTask(1L, testUser));
    }

    @Test
    @DisplayName("查询不存在的任务 → TaskNotFoundException")
    void shouldThrowWhenTaskNotFound() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(TaskNotFoundException.class, () -> taskService.getTask(999L, testUser));
    }

    @Test
    @DisplayName("分页查询")
    void shouldListTasks() {
        Task t1 = new Task(1L, "标题1", "类型1", "输入1");
        t1.setUser(testUser);
        Task t2 = new Task(2L, "标题2", "类型2", "输入2");
        t2.setUser(testUser);
        Page<Task> mockPage = new PageImpl<>(List.of(t1, t2));
        when(taskRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(mockPage);

        Page<TaskResponse> result = taskService.listTasks(null, null, null, testUser, PageRequest.of(0, 10));
        assertEquals(2, result.getTotalElements());
        assertEquals("标题1", result.getContent().get(0).getTitle());
    }

    @Test
    @DisplayName("删除自己的任务")
    void shouldDeleteOwnTask() {
        Task task = new Task(1L, "test", "type", "input");
        task.setUser(testUser);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        doNothing().when(taskRepository).deleteById(1L);

        assertDoesNotThrow(() -> taskService.deleteTask(1L, testUser));
        verify(taskRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("生成模拟结果")
    void shouldGenerateMockResult() {
        Task task = new Task(1L, "测试", "分析", "输入文本");
        task.setUser(testUser);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        AIResult aiResult = new AIResult("摘要", "结论", "建议", 50, 50, "mock");
        when(aiService.analyze("输入文本", "分析")).thenReturn(aiResult);
        when(aiService.getProvider()).thenReturn("mock");
        when(aiService.getPromptVersion()).thenReturn("mock-v1");
        when(usageLogRepository.save(any())).thenReturn(null);

        MockResultResponse response = taskService.generateResult(1L, testUser);
        assertEquals(TaskStatus.RESULT_GENERATED, response.getStatus());
        assertEquals("摘要", response.getSummary());
        assertEquals("mock", response.getModelName());
        assertEquals(100, response.getTokensUsed());
        verify(taskRepository, times(1)).save(any(Task.class));
        verify(usageLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("AI 调用失败时保存失败日志")
    void shouldSaveFailureLogOnAIError() {
        Task task = new Task(1L, "测试", "分析", "输入文本");
        task.setUser(testUser);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        AIServiceException ex = new AIServiceException("timeout", "AI 调用超时");
        when(aiService.analyze(any(), any())).thenThrow(ex);
        when(aiService.getProvider()).thenReturn("mock");
        when(aiService.getPromptVersion()).thenReturn("mock-v1");
        when(usageLogRepository.save(any())).thenReturn(null);

        assertThrows(AIServiceException.class, () -> taskService.generateResult(1L, testUser));
        // 失败日志应该被保存
        verify(usageLogRepository, times(1)).save(any());
        // 任务状态不应该被更新
        verify(taskRepository, never()).save(any(Task.class));
    }
}

// ==================== OpenAIAIService 重试逻辑测试 ====================
class OpenAIAIServiceRetryTest {

    @Test
    @DisplayName("auth 错误不应重试")
    void shouldNotRetryAuth() {
        assertFalse(OpenAIAIService.isRetryable("auth"));
    }

    @Test
    @DisplayName("parse 错误不应重试")
    void shouldNotRetryParse() {
        assertFalse(OpenAIAIService.isRetryable("parse"));
    }

    @Test
    @DisplayName("timeout 应重试")
    void shouldRetryTimeout() {
        assertTrue(OpenAIAIService.isRetryable("timeout"));
    }

    @Test
    @DisplayName("rate_limit 应重试")
    void shouldRetryRateLimit() {
        assertTrue(OpenAIAIService.isRetryable("rate_limit"));
    }

    @Test
    @DisplayName("upstream 应重试")
    void shouldRetryUpstream() {
        assertTrue(OpenAIAIService.isRetryable("upstream"));
    }

    @Test
    @DisplayName("network 应重试")
    void shouldRetryNetwork() {
        assertTrue(OpenAIAIService.isRetryable("network"));
    }

    @Test
    @DisplayName("HTTP 状态码分类：400 → bad_request")
    void shouldMap400ToBadRequest() {
        assertEquals("bad_request", OpenAIAIService.mapHttpError(400, ""));
    }

    @Test
    @DisplayName("HTTP 状态码分类：401/403 → auth")
    void shouldMapAuthCodes() {
        assertEquals("auth", OpenAIAIService.mapHttpError(401, ""));
        assertEquals("auth", OpenAIAIService.mapHttpError(403, ""));
    }

    @Test
    @DisplayName("HTTP 状态码分类：408 → timeout")
    void shouldMap408ToTimeout() {
        assertEquals("timeout", OpenAIAIService.mapHttpError(408, ""));
    }

    @Test
    @DisplayName("HTTP 状态码分类：429 → rate_limit")
    void shouldMap429ToRateLimit() {
        assertEquals("rate_limit", OpenAIAIService.mapHttpError(429, ""));
    }

    @Test
    @DisplayName("HTTP 状态码分类：500/502/503/504 → upstream")
    void shouldMap5xxToUpstream() {
        assertEquals("upstream", OpenAIAIService.mapHttpError(500, ""));
        assertEquals("upstream", OpenAIAIService.mapHttpError(502, ""));
        assertEquals("upstream", OpenAIAIService.mapHttpError(503, ""));
        assertEquals("upstream", OpenAIAIService.mapHttpError(504, ""));
    }

    @Test
    @DisplayName("HTTP 状态码分类：其他 4xx → client_error")
    void shouldMapOther4xxToClientError() {
        assertEquals("client_error", OpenAIAIService.mapHttpError(422, ""));
    }
}

// ==================== PromptTemplate 测试 ====================
class PromptTemplateTest {

    @Test
    @DisplayName("变量替换")
    void shouldReplaceVariables() {
        PromptTemplate t = new PromptTemplate("v1", "分析{taskType}: {inputText}");
        String result = t.render(Map.of("taskType", "情感", "inputText", "服务好"));
        assertEquals("分析情感: 服务好", result);
        assertEquals("v1", t.getVersion());
    }

    @Test
    @DisplayName("未匹配的变量保持原样")
    void shouldKeepUnmatchedVariables() {
        PromptTemplate t = new PromptTemplate("v1", "Hello {name}");
        assertEquals("Hello {name}", t.render(Map.of()));
    }
}