package com.example.demo.tool;

import com.example.demo.dto.FileResponse;
import com.example.demo.dto.RetrievalResult;
import com.example.demo.dto.TaskResponse;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.model.User;
import com.example.demo.service.FileService;
import com.example.demo.service.RetrievalService;
import com.example.demo.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * ToolExecutor 测试 —— 正常调用 / 非法参数 / 未知 Tool / 越权。
 */
@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

    @Mock private RetrievalService retrievalService;
    @Mock private TaskService taskService;
    @Mock private FileService fileService;

    private ToolExecutor executor;
    private final User alice = new User(1L, "alice", "pwd");
    private final User bob = new User(2L, "bob", "pwd");

    @BeforeEach
    void setUp() {
        ToolRegistry registry = new ToolRegistry(List.of(
                new SearchKnowledgeBaseTool(retrievalService),
                new QueryTaskTool(taskService),
                new GetFileInfoTool(fileService)));
        executor = new ToolExecutor(registry);
    }

    @Test
    @DisplayName("注册表包含 3 个只读 Tool，定义含参数说明与必需参数")
    void registryHasThreeTools() {
        ToolRegistry r = new ToolRegistry(List.of(
                new SearchKnowledgeBaseTool(retrievalService),
                new QueryTaskTool(taskService),
                new GetFileInfoTool(fileService)));
        assertTrue(r.allDefinitions().containsKey("searchKnowledgeBase"));
        assertTrue(r.allDefinitions().containsKey("queryTask"));
        assertTrue(r.allDefinitions().containsKey("getFileInfo"));
        assertTrue(r.byName("queryTask").definition().requiredParams().contains("taskId"));
    }

    @Test
    @DisplayName("searchKnowledgeBase 正常调用（query 必填）")
    void searchKnowledgeBaseOk() {
        when(retrievalService.search(eq("今天天气"), eq(alice), isNull())).thenReturn(List.of(
                new RetrievalResult(1L, 101L, 0, "一段较长内容需要预览截断展示", 0.9f)));
        ToolResult result = executor.execute("searchKnowledgeBase",
                Map.of("query", "今天天气"), alice);
        assertTrue(result.success());
        List<?> data = (List<?>) result.data();
        assertEquals(1, data.size());
        assertNull(result.error());
    }

    @Test
    @DisplayName("queryTask 正常调用")
    void queryTaskOk() {
        TaskResponse task = org.mockito.Mockito.mock(TaskResponse.class);
        when(taskService.getTask(5L, alice)).thenReturn(task);
        ToolResult result = executor.execute("queryTask", Map.of("taskId", 5), alice);
        assertTrue(result.success());
        assertSame(task, result.data());
    }

    @Test
    @DisplayName("getFileInfo 正常调用")
    void getFileInfoOk() {
        FileResponse file = org.mockito.Mockito.mock(FileResponse.class);
        when(fileService.getFile(7L, alice)).thenReturn(file);
        ToolResult result = executor.execute("getFileInfo", Map.of("fileId", "7"), alice);
        assertTrue(result.success());
        assertSame(file, result.data());
    }

    @Test
    @DisplayName("非法参数：缺少必需参数 → error")
    void missingRequiredParam() {
        ToolResult result = executor.execute("queryTask", Map.of(), alice);
        assertFalse(result.success());
        assertTrue(result.error().contains("缺少必需参数: taskId"));
    }

    @Test
    @DisplayName("非法参数：taskId 非数字 → error")
    void nonNumericTaskId() {
        ToolResult result = executor.execute("queryTask", Map.of("taskId", "abc"), alice);
        assertFalse(result.success());
        assertTrue(result.error().contains("不是合法数字"));
    }

    @Test
    @DisplayName("未知 Tool → error")
    void unknownTool() {
        ToolResult result = executor.execute("deleteEverything", Map.of(), alice);
        assertFalse(result.success());
        assertTrue(result.error().contains("未知工具"));
    }

    @Test
    @DisplayName("越权：查询他人任务 → error（TaskService 归属校验抛 Forbidden）")
    void forbiddenTask() {
        when(taskService.getTask(9L, alice))
                .thenThrow(new ForbiddenException("无权访问该任务"));
        ToolResult result = executor.execute("queryTask", Map.of("taskId", 9L), alice);
        assertFalse(result.success());
        assertTrue(result.error().contains("无权访问"));
    }

    @Test
    @DisplayName("越权：查询他人文件 → error")
    void forbiddenFile() {
        when(fileService.getFile(9L, alice))
                .thenThrow(new ForbiddenException("无权访问该文件"));
        ToolResult result = executor.execute("getFileInfo", Map.of("fileId", 9L), alice);
        assertFalse(result.success());
        assertTrue(result.error().contains("无权访问"));
    }

    @Test
    @DisplayName("不存在的任务/文件 → 明确 error，不抛异常")
    void notFoundResources() {
        when(taskService.getTask(99L, alice))
                .thenThrow(new com.example.demo.exception.TaskNotFoundException(99L));
        ToolResult taskResult = executor.execute("queryTask", Map.of("taskId", 99L), alice);
        assertFalse(taskResult.success());
        assertTrue(taskResult.error().contains("不存在"));

        when(fileService.getFile(88L, alice))
                .thenThrow(new com.example.demo.exception.FileRecordNotFoundException(88L));
        ToolResult fileResult = executor.execute("getFileInfo", Map.of("fileId", 88L), alice);
        assertFalse(fileResult.success());
        assertTrue(fileResult.error().contains("不存在"));
    }
}
