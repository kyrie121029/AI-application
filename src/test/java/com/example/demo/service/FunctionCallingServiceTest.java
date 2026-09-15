package com.example.demo.service;

import com.example.demo.config.AgentProperties;
import com.example.demo.dto.AgentResult;
import com.example.demo.dto.AgentTrace;
import com.example.demo.dto.AIResult;
import com.example.demo.dto.ChatMessage;
import com.example.demo.dto.ToolCall;
import com.example.demo.enums.AgentStatus;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.model.Message;
import com.example.demo.model.User;
import com.example.demo.tool.GetFileInfoTool;
import com.example.demo.tool.QueryTaskTool;
import com.example.demo.tool.SearchKnowledgeBaseTool;
import com.example.demo.tool.ToolErrorCode;
import com.example.demo.tool.ToolExecutor;
import com.example.demo.tool.ToolRegistry;
import com.example.demo.dto.RetrievalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FunctionCallingService（受控多轮 Agent Loop）测试。
 * FakeAi 记录每次 LLM 调用并按序弹回预设 ChatMessage；底层只读 Service 用 mock。
 */
@ExtendWith(MockitoExtension.class)
class FunctionCallingServiceTest {

    @Mock private RetrievalService retrievalService;
    @Mock private TaskService taskService;
    @Mock private FileService fileService;

    private FakeAi fakeAi;
    private AgentProperties agentProperties;
    private FunctionCallingService service;
    private final User alice = new User(1L, "alice", "pwd");

    @BeforeEach
    void setUp() {
        fakeAi = new FakeAi();
        agentProperties = new AgentProperties();
        agentProperties.setMaxRounds(5);
        agentProperties.setTimeout(java.time.Duration.ofSeconds(30));
        ToolRegistry registry = new ToolRegistry(List.of(
                new SearchKnowledgeBaseTool(retrievalService),
                new QueryTaskTool(taskService),
                new GetFileInfoTool(fileService)));
        service = new FunctionCallingService(fakeAi, registry, new ToolExecutor(registry), agentProperties);
    }

    private ToolCall call(String id, String name, Map<String, Object> args) {
        return new ToolCall(id, name, args);
    }

    private ChatMessage toolReply(String name, Map<String, Object> args) {
        return ChatMessage.assistant(null, List.of(call("id-" + name, name, args)));
    }

    @Test
    @DisplayName("多轮 Tool 调用成功：searchKB → queryTask → 最终答案")
    void multiRoundSuccess() {
        when(retrievalService.search(eq("x"), eq(alice), eq(null)))
                .thenReturn(List.of(new RetrievalResult(1L, 101L, 0, "资料", 0.9f)));
        when(taskService.getTask(2L, alice)).thenReturn(
                org.mockito.Mockito.mock(com.example.demo.dto.TaskResponse.class));

        fakeAi.reply(toolReply("searchKnowledgeBase", Map.of("query", "x")));
        fakeAi.reply(toolReply("queryTask", Map.of("taskId", 2L)));
        fakeAi.reply(ChatMessage.assistant("最终答案", null));

        AgentResult result = service.run("sys", "查资料再查任务", alice);

        assertEquals(AgentStatus.SUCCESS, result.status());
        assertEquals("最终答案", result.answer());
        assertEquals(2, result.trace().size());
        assertEquals("searchKnowledgeBase", result.trace().get(0).toolName());
        assertEquals("queryTask", result.trace().get(1).toolName());
        assertEquals(2, result.roundsUsed());
        verify(taskService).getTask(2L, alice);
    }

    @Test
    @DisplayName("maxRounds 到达 → MAX_ROUNDS 安全终止")
    void maxRoundsTerminates() {
        agentProperties.setMaxRounds(1);
        fakeAi.reply(toolReply("queryTask", Map.of("taskId", 2L)));
        when(taskService.getTask(2L, alice)).thenReturn(
                org.mockito.Mockito.mock(com.example.demo.dto.TaskResponse.class));

        AgentResult result = service.run("sys", "q", alice);

        assertEquals(AgentStatus.MAX_ROUNDS, result.status());
        assertEquals(1, result.trace().size());
    }

    @Test
    @DisplayName("重复 Tool + 相同参数 → DUPLICATE_CALL 终止，第二次不执行")
    void duplicateCallTerminates() {
        when(taskService.getTask(2L, alice)).thenReturn(
                org.mockito.Mockito.mock(com.example.demo.dto.TaskResponse.class));
        fakeAi.reply(toolReply("queryTask", Map.of("taskId", 2L)));
        fakeAi.reply(toolReply("queryTask", Map.of("taskId", 2L))); // 重复

        AgentResult result = service.run("sys", "q", alice);

        assertEquals(AgentStatus.DUPLICATE_CALL, result.status());
        assertEquals(1, result.trace().size());
        verify(taskService, times(1)).getTask(2L, alice); // 只执行一次
    }

    @Test
    @DisplayName("Tool 参数错误（INVALID_ARGUMENT）后模型可重新决策并成功")
    void invalidArgThenReDecision() {
        when(taskService.getTask(5L, alice)).thenReturn(
                org.mockito.Mockito.mock(com.example.demo.dto.TaskResponse.class));
        fakeAi.reply(toolReply("queryTask", Map.of("taskId", "abc"))); // 非法参数
        fakeAi.reply(toolReply("queryTask", Map.of("taskId", 5L)));
        fakeAi.reply(ChatMessage.assistant("答案", null));

        AgentResult result = service.run("sys", "q", alice);

        assertEquals(AgentStatus.SUCCESS, result.status());
        assertEquals(2, result.trace().size());
        assertEquals(ToolErrorCode.INVALID_ARGUMENT.name(), result.trace().get(0).errorCode());
        assertFalse(result.trace().get(0).success());
        assertTrue(result.trace().get(1).success());
        verify(taskService).getTask(5L, alice);
    }

    @Test
    @DisplayName("Forbidden → 立即终止且不重复执行")
    void forbiddenStops() {
        when(taskService.getTask(9L, alice))
                .thenThrow(new ForbiddenException("无权访问该任务"));
        fakeAi.reply(toolReply("queryTask", Map.of("taskId", 9L)));

        AgentResult result = service.run("sys", "q", alice);

        assertEquals(AgentStatus.FORBIDDEN, result.status());
        assertEquals(1, result.trace().size());
        assertEquals(ToolErrorCode.FORBIDDEN.name(), result.trace().get(0).errorCode());
        verify(taskService, times(1)).getTask(9L, alice);
    }

    @Test
    @DisplayName("无 Tool 直接回答 → SUCCESS，trace 为空")
    void noToolDirectAnswer() {
        fakeAi.reply(ChatMessage.assistant("直接回答", null));

        AgentResult result = service.run("sys", "hi", alice);

        assertEquals(AgentStatus.SUCCESS, result.status());
        assertEquals("直接回答", result.answer());
        assertTrue(result.trace().isEmpty());
    }

    @Test
    @DisplayName("Trace 顺序与轮次正确，latency 非负")
    void traceOrderAndRounds() {
        when(taskService.getTask(2L, alice)).thenReturn(
                org.mockito.Mockito.mock(com.example.demo.dto.TaskResponse.class));
        fakeAi.reply(toolReply("queryTask", Map.of("taskId", 2L)));
        fakeAi.reply(ChatMessage.assistant("ok", null));

        AgentResult result = service.run("sys", "q", alice);

        assertEquals(1, result.roundsUsed());
        AgentTrace t = result.trace().get(0);
        assertEquals(1, t.round());
        assertEquals("queryTask", t.toolName());
        assertTrue(t.latencyMs() >= 0);
        assertTrue(t.success());
        assertNull(t.error());
    }

    /** 真 AIService 假实现：记录调用并按序弹回 ChatMessage */
    private static final class FakeAi implements AIService {

        final List<List<ChatMessage>> calls = new ArrayList<>();
        final List<ChatMessage> replies = new ArrayList<>();
        int index = 0;

        void reply(ChatMessage m) {
            replies.add(m);
        }

        @Override
        public ChatMessage chatCompletion(List<ChatMessage> messages, List<Map<String, Object>> toolSchemas) {
            calls.add(messages);
            return replies.get(index++);
        }

        @Override
        public AIResult analyze(String inputText, String taskType) {
            return null;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            return "";
        }

        @Override
        public void streamAnalyze(String systemPrompt, List<Message> history, BooleanSupplier isCancelled,
                                  Consumer<String> onChunk, Runnable onComplete,
                                  Consumer<Throwable> onError, AtomicInteger retryCountOut) {
        }

        @Override
        public String getProvider() {
            return "mock";
        }

        @Override
        public String getPromptVersion() {
            return "v";
        }

        @Override
        public String getModelName() {
            return "mock";
        }
    }
}
