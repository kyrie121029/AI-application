package com.example.demo.service;

import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.ConversationResponse;
import com.example.demo.enums.MessageRole;
import com.example.demo.exception.AIServiceException;
import com.example.demo.exception.DuplicateRequestException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.InputTooLongException;
import com.example.demo.model.AIUsageLog;
import com.example.demo.model.Conversation;
import com.example.demo.model.Message;
import com.example.demo.model.User;
import com.example.demo.repository.AIUsageLogRepository;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ConversationRepository conversationRepo;
    @Mock private MessageRepository messageRepo;
    @Mock private ChatPersistenceService persistenceService;
    @Mock private AIService aiService;
    @Mock private AIUsageLogRepository usageLogRepository;
    @Mock private TokenEstimator tokenEstimator;
    @InjectMocks private ChatService chatService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User(1L, "alice", "pwd");
        ReflectionTestUtils.setField(chatService, "chatSystemPrompt", "你是测试助手。");
        ReflectionTestUtils.setField(chatService, "chatPromptVersion", "chat-system-v1");
        ReflectionTestUtils.setField(chatService, "maxInputTokens", 100000);
        ReflectionTestUtils.setField(chatService, "maxOutputTokens", 1000);
        // lenient：不是所有测试都会调用 loadContext
        lenient().when(tokenEstimator.estimateTokens(any())).thenReturn(1); // 每条消息 1 token
    }

    private Conversation ownedConversation(Long id) {
        Conversation c = new Conversation("test", testUser);
        c.setId(id);
        return c;
    }

    /** streamAnalyze 参数回调（带类型） */
    @FunctionalInterface
    private interface StreamAnswer {
        void run(BooleanSupplier isCancelled, Consumer<String> onChunk,
                 Runnable onComplete, Consumer<Throwable> onError);
    }

    @SuppressWarnings("unchecked")
    private void stubStreamAnalyze(StreamAnswer answer) {
        doAnswer(inv -> {
            BooleanSupplier isCancelled = inv.getArgument(2);
            Consumer<String> onChunk = inv.getArgument(3);
            Runnable onComplete = inv.getArgument(4);
            Consumer<Throwable> onError = inv.getArgument(5);
            answer.run(isCancelled, onChunk, onComplete, onError);
            return null;
        }).when(aiService).streamAnalyze(anyString(), anyList(), any(BooleanSupplier.class), any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private void stubStreamAnalyzeWithRetryCount(StreamAnswerWithRetryCount answer) {
        doAnswer(inv -> {
            BooleanSupplier isCancelled = inv.getArgument(2);
            Consumer<String> onChunk = inv.getArgument(3);
            Runnable onComplete = inv.getArgument(4);
            Consumer<Throwable> onError = inv.getArgument(5);
            AtomicInteger retryCountOut = inv.getArgument(6);
            answer.run(isCancelled, onChunk, onComplete, onError, retryCountOut);
            return null;
        }).when(aiService).streamAnalyze(anyString(), anyList(), any(BooleanSupplier.class), any(), any(), any(), any());
    }

    @FunctionalInterface
    private interface StreamAnswerWithRetryCount {
        void run(BooleanSupplier isCancelled, Consumer<String> onChunk,
                 Runnable onComplete, Consumer<Throwable> onError, AtomicInteger retryCountOut);
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<List<Message>> captureHistory() {
        AtomicReference<List<Message>> captured = new AtomicReference<>();
        doAnswer(inv -> {
            captured.set((List<Message>) inv.getArgument(1));
            return null;
        }).when(aiService).streamAnalyze(anyString(), anyList(), any(BooleanSupplier.class), any(), any(), any(), any());
        return captured;
    }

    private ChatRequest request(String content) {
        ChatRequest req = new ChatRequest();
        req.setContent(content);
        return req;
    }

    private ChatRequest request(String content, String requestId) {
        ChatRequest req = request(content);
        req.setRequestId(requestId);
        return req;
    }

    // ==================== 对话列表 / 历史 / 删除 ====================

    @Test
    @DisplayName("对话列表只返回当前用户的")
    void shouldListOwnConversations() {
        when(conversationRepo.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(ownedConversation(1L)));
        List<ConversationResponse> result = chatService.listConversations(testUser);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("非会话所有者访问历史 → ForbiddenException")
    void shouldRejectOtherUsersMessages() {
        User bob = new User(2L, "bob", "pwd");
        Conversation c = new Conversation("bob's", bob);
        c.setId(1L);
        when(persistenceService.findConversation(1L)).thenReturn(c);
        assertThrows(ForbiddenException.class, () -> chatService.getMessages(1L, testUser));
    }

    @Test
    @DisplayName("删除他人会话 → ForbiddenException")
    void shouldRejectOtherUsersDelete() {
        User bob = new User(2L, "bob", "pwd");
        Conversation c = new Conversation("bob's", bob);
        c.setId(1L);
        when(persistenceService.findConversation(1L)).thenReturn(c);
        assertThrows(ForbiddenException.class, () -> chatService.deleteConversation(1L, testUser));
    }

    @Test
    @DisplayName("删除自己的会话 → 调用持久化删除")
    void shouldDeleteOwnConversation() {
        when(persistenceService.findConversation(1L)).thenReturn(ownedConversation(1L));
        chatService.deleteConversation(1L, testUser);
        verify(persistenceService).deleteConversation(1L);
    }

    // ==================== 新建对话 ====================

    @Test
    @DisplayName("新建对话：首条 USER 消息传入 AIService")
    void shouldPassFirstUserMessageToAI() throws Exception {
        Conversation c = ownedConversation(1L);
        Message firstMsg = new Message(c, MessageRole.USER, "你好");
        when(persistenceService.createConversation(anyString(), any(), anyString())).thenReturn(c);
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(firstMsg));
        AtomicReference<List<Message>> captured = captureHistory();

        chatService.startConversation(request("你好"), testUser);
        Thread.sleep(100);

        List<Message> history = captured.get();
        assertNotNull(history, "首条消息必须传给模型");
        assertEquals(1, history.size());
        assertEquals(MessageRole.USER, history.get(0).getRole());
        assertEquals("你好", history.get(0).getContent());
    }

    @Test
    @DisplayName("新建对话：不重复保存首条消息")
    void shouldNotDoubleSaveFirstMessage() {
        Conversation c = ownedConversation(1L);
        when(persistenceService.createConversation(anyString(), any(), anyString())).thenReturn(c);
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        captureHistory();

        chatService.startConversation(request("你好"), testUser);

        verify(persistenceService, times(1)).createConversation(anyString(), any(), anyString());
        verify(persistenceService, never()).saveUserMessage(any(), anyString(), any());
    }

    // ==================== 继续对话 ====================

    @Test
    @DisplayName("继续对话：上下文按时间升序传给模型")
    void shouldLoadContextAscending() throws Exception {
        when(persistenceService.findConversation(1L)).thenReturn(ownedConversation(1L));

        List<Message> recentDesc = new ArrayList<>();
        for (int i = 24; i >= 5; i--) {
            Message m = new Message(ownedConversation(1L), MessageRole.USER, "msg" + i);
            m.setId((long) i);
            recentDesc.add(m); // 24..5 倒序
        }
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(recentDesc);
        AtomicReference<List<Message>> captured = captureHistory();

        chatService.continueConversation(1L, request("新消息"), testUser);
        Thread.sleep(100);

        List<Message> history = captured.get();
        assertNotNull(history);
        assertEquals(20, history.size());
        assertEquals(5L, history.get(0).getId(), "最旧在前");
        assertEquals(24L, history.get(19).getId(), "最新在后");
        verify(persistenceService).saveUserMessage(1L, "新消息", null);
    }

    @Test
    @DisplayName("继续对话：Token 预算不足时截断，当前用户消息始终保留")
    void shouldTruncateByTokenBudget() throws Exception {
        ReflectionTestUtils.setField(chatService, "maxInputTokens", 10);
        ReflectionTestUtils.setField(chatService, "maxOutputTokens", 1);
        when(persistenceService.findConversation(1L)).thenReturn(ownedConversation(1L));

        List<Message> recentDesc = new ArrayList<>();
        for (int i = 9; i >= 0; i--) {
            Message m = new Message(ownedConversation(1L), MessageRole.USER, "msg" + i);
            m.setId((long) i);
            recentDesc.add(m); // 9..0 倒序
        }
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(recentDesc);
        AtomicReference<List<Message>> captured = captureHistory();

        chatService.continueConversation(1L, request("新消息"), testUser);
        Thread.sleep(100);

        List<Message> history = captured.get();
        assertNotNull(history);
        // 预算语义：maxInputTokens 是总预算 → 历史预算 = 10 - system(1) - current(1) - output(1) = 7
        // 当前消息(9)始终保留；历史按 recent-first：8,7,6,5,4,3,2 恰好 7 条；id1(7+1>7) 被排除
        assertEquals(8, history.size(), "应按 Token 预算截断（当前消息 + 7 条历史）");
        assertEquals(2L, history.get(0).getId(), "最旧保留 id=2");
        assertEquals(9L, history.get(history.size() - 1).getId(), "当前用户消息(最新)始终保留");
    }

    @Test
    @DisplayName("继续对话：非所有者 → ForbiddenException，不保存消息")
    void shouldRejectContinueForNonOwner() {
        User bob = new User(2L, "bob", "pwd");
        Conversation c = new Conversation("bob's", bob);
        c.setId(1L);
        when(persistenceService.findConversation(1L)).thenReturn(c);
        assertThrows(ForbiddenException.class,
                () -> chatService.continueConversation(1L, request("hi"), testUser));
        verify(persistenceService, never()).saveUserMessage(any(), anyString(), any());
    }

    @Test
    @DisplayName("继续对话：重复 requestId → DuplicateRequestException，不调用模型")
    void shouldRejectDuplicateRequestId() {
        when(persistenceService.findConversation(1L)).thenReturn(ownedConversation(1L));
        when(persistenceService.userMessageExists(1L, "req-1")).thenReturn(true);

        assertThrows(DuplicateRequestException.class,
                () -> chatService.continueConversation(1L, request("hi", "req-1"), testUser));
        verify(persistenceService, never()).saveUserMessage(any(), anyString(), any());
        verify(aiService, never()).streamAnalyze(anyString(), anyList(), any(), any(), any(), any(), any());
    }

    // ==================== 流式终态保护 ====================

    @Test
    @DisplayName("onComplete 被重复调用 → ASSISTANT 只保存一次，成功日志写一次")
    void shouldSaveAssistantOnlyOnceWhenCompleteCalledTwice() throws Exception {
        Conversation c = ownedConversation(1L);
        Message firstMsg = new Message(c, MessageRole.USER, "hi");
        when(persistenceService.createConversation(anyString(), any(), anyString())).thenReturn(c);
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(firstMsg));

        stubStreamAnalyze((isCancelled, onChunk, onComplete, onError) -> {
            onChunk.accept("回复");
            onComplete.run();
            onComplete.run(); // 重复调用
        });

        chatService.startConversation(request("hi"), testUser);
        Thread.sleep(200);

        verify(persistenceService, times(1)).saveAssistantMessage(eq(1L), eq("回复"));
        ArgumentCaptor<AIUsageLog> captor = ArgumentCaptor.forClass(AIUsageLog.class);
        verify(usageLogRepository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().isSuccess());
    }

    @Test
    @DisplayName("onError 后又触发 onComplete → 不保存 ASSISTANT，写失败日志")
    void shouldNotSaveAssistantAfterError() throws Exception {
        Conversation c = ownedConversation(1L);
        Message firstMsg = new Message(c, MessageRole.USER, "hi");
        when(persistenceService.createConversation(anyString(), any(), anyString())).thenReturn(c);
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(firstMsg));

        stubStreamAnalyze((isCancelled, onChunk, onComplete, onError) -> {
            onChunk.accept("部分内容");
            onError.accept(new AIServiceException("rate_limit", "限流"));
            onComplete.run(); // 错误之后又触发完成
        });

        chatService.startConversation(request("hi"), testUser);
        Thread.sleep(200);

        verify(persistenceService, never()).saveAssistantMessage(any(), anyString());
        ArgumentCaptor<AIUsageLog> captor = ArgumentCaptor.forClass(AIUsageLog.class);
        verify(usageLogRepository, times(1)).save(captor.capture());
        assertFalse(captor.getValue().isSuccess());
        assertEquals("rate_limit", captor.getValue().getErrorType());
    }

    @Test
    @DisplayName("onError 后继续推送 chunk → 被忽略，不抛异常")
    void shouldIgnoreChunksAfterError() throws Exception {
        Conversation c = ownedConversation(1L);
        Message firstMsg = new Message(c, MessageRole.USER, "hi");
        when(persistenceService.createConversation(anyString(), any(), anyString())).thenReturn(c);
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(firstMsg));

        stubStreamAnalyze((isCancelled, onChunk, onComplete, onError) -> {
            onError.accept(new AIServiceException("upstream", "上游错误"));
            onChunk.accept("迟到内容");
        });

        assertDoesNotThrow(() -> chatService.startConversation(request("hi"), testUser));
        Thread.sleep(200);
        verify(persistenceService, never()).saveAssistantMessage(any(), anyString());
    }

    @Test
    @DisplayName("onError 后 isCancelled 标志变为 true（客户端断开传播）")
    void shouldSetCancelledAfterError() throws Exception {
        Conversation c = ownedConversation(1L);
        Message firstMsg = new Message(c, MessageRole.USER, "hi");
        when(persistenceService.createConversation(anyString(), any(), anyString())).thenReturn(c);
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(firstMsg));

        AtomicReference<BooleanSupplier> cancelledRef = new AtomicReference<>();
        stubStreamAnalyze((isCancelled, onChunk, onComplete, onError) -> {
            cancelledRef.set(isCancelled);
            onError.accept(new AIServiceException("timeout", "超时"));
        });

        chatService.startConversation(request("hi"), testUser);
        Thread.sleep(200);

        assertNotNull(cancelledRef.get());
        assertTrue(cancelledRef.get().getAsBoolean(), "客户端断开后 AI 应感知取消");
    }

    @Test
    @DisplayName("正常完成时记录首 Token 延迟与耗时（成功日志）")
    void shouldLogFirstTokenLatencyOnSuccess() throws Exception {
        Conversation c = ownedConversation(1L);
        Message firstMsg = new Message(c, MessageRole.USER, "hi");
        when(persistenceService.createConversation(anyString(), any(), anyString())).thenReturn(c);
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(firstMsg));

        stubStreamAnalyze((isCancelled, onChunk, onComplete, onError) -> {
            onChunk.accept("A");
            onChunk.accept("B");
            onComplete.run();
        });

        chatService.startConversation(request("hi"), testUser);
        Thread.sleep(200);

        ArgumentCaptor<AIUsageLog> captor = ArgumentCaptor.forClass(AIUsageLog.class);
        verify(usageLogRepository, times(1)).save(captor.capture());
        AIUsageLog log = captor.getValue();
        assertEquals(1L, log.getConversationId());
        assertTrue(log.isSuccess());
        assertTrue(log.getFirstTokenLatencyMs() >= 0);
        assertTrue(log.getElapsedMs() >= 0);
        assertEquals("chat-system-v1", log.getPromptVersion());
    }

    // ==================== Token 预算精确边界 ====================

    @Test
    @DisplayName("Token 预算精确边界：used+t == budget 恰好包含，超过则排除")
    void shouldIncludeMessageExactlyAtBudgetBoundary() throws Exception {
        ReflectionTestUtils.setField(chatService, "maxInputTokens", 6);
        ReflectionTestUtils.setField(chatService, "maxOutputTokens", 1);
        when(persistenceService.findConversation(1L)).thenReturn(ownedConversation(1L));

        List<Message> recentDesc = new ArrayList<>();
        for (int i = 9; i >= 0; i--) {
            Message m = new Message(ownedConversation(1L), MessageRole.USER, "msg" + i);
            m.setId((long) i);
            recentDesc.add(m); // 9..0 倒序
        }
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(recentDesc);
        AtomicReference<List<Message>> captured = captureHistory();

        chatService.continueConversation(1L, request("新消息"), testUser);
        Thread.sleep(100);

        List<Message> history = captured.get();
        assertNotNull(history);
        // 历史预算 = 6 - system(1) - current(1) - output(1) = 3
        // 当前 id9 恒保留；id8(used=1), id7(=2), id6(=3==budget 恰好) 包含；id5(4>3) 排除
        assertEquals(4, history.size(), "恰好用满预算的边界消息应被包含");
        assertEquals(6L, history.get(0).getId(), "最旧保留 id=6（恰好用满历史预算）");
        assertEquals(9L, history.get(history.size() - 1).getId(), "当前用户消息始终保留");
    }

    @Test
    @DisplayName("当前消息本身超出 Token 预算 → InputTooLongException，不静默截断")
    void currentMessageOverBudgetThrowsInputTooLong() {
        ReflectionTestUtils.setField(chatService, "maxInputTokens", 2);
        ReflectionTestUtils.setField(chatService, "maxOutputTokens", 1);
        when(persistenceService.findConversation(1L)).thenReturn(ownedConversation(1L));
        // estimator 固定返回 1：system(1) + current(1) + output(1) = 3 > 2 → 抛异常
        Message tooLong = new Message(ownedConversation(1L), MessageRole.USER, "超长消息");
        tooLong.setId(9L);
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(tooLong));

        assertThrows(InputTooLongException.class,
                () -> chatService.continueConversation(1L, request("超长消息"), testUser));
        // 超预算时不构造模型请求，也不静默截断 —— 以异常明确反馈
        verify(aiService, never()).streamAnalyze(anyString(), anyList(), any(), any(), any(), any(), any());
    }

    // ==================== retryCount 从 AI 实现写回 ====================

    @Test
    @DisplayName("成功路径记录 AI 实现写回的真实 retryCount")
    void shouldRecordRetryCountFromSinkOnSuccess() throws Exception {
        Conversation c = ownedConversation(1L);
        Message firstMsg = new Message(c, MessageRole.USER, "hi");
        when(persistenceService.createConversation(anyString(), any(), anyString())).thenReturn(c);
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(firstMsg));

        stubStreamAnalyzeWithRetryCount((isCancelled, onChunk, onComplete, onError, retryCountOut) -> {
            retryCountOut.set(2);
            onComplete.run();
        });

        chatService.startConversation(request("hi"), testUser);
        Thread.sleep(200);

        ArgumentCaptor<AIUsageLog> captor = ArgumentCaptor.forClass(AIUsageLog.class);
        verify(usageLogRepository, times(1)).save(captor.capture());
        assertEquals(2, captor.getValue().getRetryCount());
        assertTrue(captor.getValue().isSuccess());
    }

    @Test
    @DisplayName("失败路径记录 AI 实现写回的真实 retryCount")
    void shouldRecordRetryCountFromSinkOnError() throws Exception {
        Conversation c = ownedConversation(1L);
        Message firstMsg = new Message(c, MessageRole.USER, "hi");
        when(persistenceService.createConversation(anyString(), any(), anyString())).thenReturn(c);
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(firstMsg));

        stubStreamAnalyzeWithRetryCount((isCancelled, onChunk, onComplete, onError, retryCountOut) -> {
            retryCountOut.set(1);
            onError.accept(new AIServiceException("rate_limit", "限流"));
        });

        chatService.startConversation(request("hi"), testUser);
        Thread.sleep(200);

        ArgumentCaptor<AIUsageLog> captor = ArgumentCaptor.forClass(AIUsageLog.class);
        verify(usageLogRepository, times(1)).save(captor.capture());
        assertEquals(1, captor.getValue().getRetryCount());
        assertEquals("rate_limit", captor.getValue().getErrorType());
        assertFalse(captor.getValue().isSuccess());
    }

    // ==================== onComplete 三类失败拆分 ====================

    @Test
    @DisplayName("Assistant 保存成功但 done 发送失败 → 不误报 persistence，不写第二条日志")
    void doneSendFailureShouldNotRecordPersistence() throws Exception {
        SseEmitter brokenEmitter = mock(SseEmitter.class);
        // 生产代码 emitter.send(SseEmitter.event()...) 静态类型是 SseEventBuilder → 命中 send(SseEventBuilder) 重载
        doThrow(new IOException("client gone"))
                .when(brokenEmitter).send(any(SseEmitter.SseEventBuilder.class));

        AtomicBoolean terminated = new AtomicBoolean(false);
        // saveAssistantMessage 是 no-op mock；只有 done 发送抛 IOException 才会让 result=false
        boolean result = chatService.completeStreamSuccessfully(brokenEmitter, terminated, 1L, 1L,
                System.currentTimeMillis(), new long[]{0}, new AtomicInteger(0), "回复内容");

        assertFalse(result, "done 发送失败应返回 false");
        verify(brokenEmitter).send(any(SseEmitter.SseEventBuilder.class)); // 确认真实命中对应重载
        ArgumentCaptor<AIUsageLog> captor = ArgumentCaptor.forClass(AIUsageLog.class);
        verify(usageLogRepository, times(1)).save(captor.capture());
        AIUsageLog log = captor.getValue();
        assertTrue(log.isSuccess(), "Assistant 已落库，日志应记成功");
        assertNull(log.getErrorType(), "done 发送失败不得误报 persistence");
    }

    @Test
    @DisplayName("Assistant 保存失败 → 记录 persistence 失败日志")
    void assistantSaveFailureRecordsPersistence() {
        doThrow(new RuntimeException("db down"))
                .when(persistenceService).saveAssistantMessage(any(), anyString());
        SseEmitter emitter = mock(SseEmitter.class);

        boolean result = chatService.completeStreamSuccessfully(emitter, new AtomicBoolean(false),
                1L, 1L, System.currentTimeMillis(), new long[]{0}, new AtomicInteger(0), "回复");

        assertFalse(result);
        ArgumentCaptor<AIUsageLog> captor = ArgumentCaptor.forClass(AIUsageLog.class);
        verify(usageLogRepository, times(1)).save(captor.capture());
        assertEquals("persistence", captor.getValue().getErrorType());
        assertFalse(captor.getValue().isSuccess());
    }

    // ==================== emitter.onError 记录失败日志 ====================

    @Test
    @DisplayName("emitter 连接异常首次到达终态 → 记录 cancelled 失败日志")
    void emitterErrorRecordsCancelledLogWhenFirstTerminal() {
        AtomicBoolean terminated = new AtomicBoolean(false);
        AtomicInteger retryCount = new AtomicInteger(0);

        boolean won = chatService.onEmitterError(new RuntimeException("conn reset"), terminated,
                1L, 1L, System.currentTimeMillis(), new long[]{0}, retryCount);

        assertTrue(won);
        assertTrue(terminated.get(), "onEmitterError 应抢占终态");
        ArgumentCaptor<AIUsageLog> captor = ArgumentCaptor.forClass(AIUsageLog.class);
        verify(usageLogRepository, times(1)).save(captor.capture());
        assertEquals("cancelled", captor.getValue().getErrorType());
        assertFalse(captor.getValue().isSuccess());
    }

    @Test
    @DisplayName("emitter 异常二次到达终态 → 不再写第二条日志")
    void emitterErrorSecondTerminalIsIgnored() {
        AtomicBoolean terminated = new AtomicBoolean(false);
        chatService.onEmitterError(new RuntimeException("first"), terminated,
                1L, 1L, System.currentTimeMillis(), new long[]{0}, new AtomicInteger(0));
        chatService.onEmitterError(new RuntimeException("second"), terminated,
                1L, 1L, System.currentTimeMillis(), new long[]{0}, new AtomicInteger(0));
        verify(usageLogRepository, times(1)).save(any());
    }

    // ==================== 终态竞争只写一次日志 ====================

    @Test
    @DisplayName("onComplete 与 onError 竞争 → 只写一次终态日志（先到者胜）")
    void onCompleteThenOnErrorWritesSingleLog() throws Exception {
        Conversation c = ownedConversation(1L);
        Message firstMsg = new Message(c, MessageRole.USER, "hi");
        when(persistenceService.createConversation(anyString(), any(), anyString())).thenReturn(c);
        when(messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(firstMsg));

        stubStreamAnalyze((isCancelled, onChunk, onComplete, onError) -> {
            onComplete.run();
            onError.accept(new AIServiceException("upstream", "迟到错误"));
        });

        chatService.startConversation(request("hi"), testUser);
        Thread.sleep(200);

        ArgumentCaptor<AIUsageLog> captor = ArgumentCaptor.forClass(AIUsageLog.class);
        verify(usageLogRepository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().isSuccess());
        verify(persistenceService, times(1)).saveAssistantMessage(eq(1L), eq(""));
    }
}
