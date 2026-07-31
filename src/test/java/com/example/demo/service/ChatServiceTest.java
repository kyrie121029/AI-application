package com.example.demo.service;

import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.ConversationResponse;
import com.example.demo.dto.MessageResponse;
import com.example.demo.model.Conversation;
import com.example.demo.model.Message;
import com.example.demo.model.User;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ConversationRepository conversationRepo;
    @Mock private MessageRepository messageRepo;
    @Mock private AIService aiService;
    @InjectMocks private ChatService chatService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User(1L, "alice", "pwd");
        // 注入 System Prompt（@Value 在纯 Mockito 测试中不生效）
        ReflectionTestUtils.setField(chatService, "chatSystemPrompt", "你是测试助手。");
    }

    @Test
    @DisplayName("对话列表只返回当前用户的")
    void shouldListOwnConversations() {
        Conversation c = new Conversation("test", testUser);
        c.setId(1L);
        when(conversationRepo.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(c));

        List<ConversationResponse> result = chatService.listConversations(testUser);
        assertEquals(1, result.size());
        assertEquals("test", result.get(0).getTitle());
    }

    @Test
    @DisplayName("获取消息时校验归属")
    void shouldCheckOwnershipForMessages() {
        Conversation c = new Conversation("test", testUser);
        c.setId(1L);
        when(conversationRepo.findById(1L)).thenReturn(Optional.of(c));
        when(messageRepo.findByConversationIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        assertDoesNotThrow(() -> chatService.getMessages(1L, testUser));
    }

    @Test
    @DisplayName("获取他人对话消息 → 抛异常")
    void shouldRejectOtherUsersMessages() {
        User bob = new User(2L, "bob", "pwd");
        Conversation c = new Conversation("bob's", bob);
        c.setId(1L);
        when(conversationRepo.findById(1L)).thenReturn(Optional.of(c));

        assertThrows(RuntimeException.class, () -> chatService.getMessages(1L, testUser));
    }

    @Test
    @DisplayName("删除对话时校验归属")
    void shouldCheckOwnershipForDelete() {
        Conversation c = new Conversation("test", testUser);
        c.setId(1L);
        when(conversationRepo.findById(1L)).thenReturn(Optional.of(c));
        when(messageRepo.findByConversationIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        doNothing().when(conversationRepo).delete(c);

        assertDoesNotThrow(() -> chatService.deleteConversation(1L, testUser));
        verify(conversationRepo, times(1)).delete(c);
    }

    @Test
    @DisplayName("SSE 流式：onComplete 会保存 ASSISTANT 消息")
    void shouldSaveAssistantMessageOnComplete() throws Exception {
        Conversation c = new Conversation("test", testUser);
        c.setId(1L);
        // 模拟 AI 流式：立刻调 onChunk 再调 onComplete
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var onChunk = (java.util.function.Consumer<String>) inv.getArgument(2);
            var onComplete = (Runnable) inv.getArgument(3);
            onChunk.accept("hello ");
            onChunk.accept("world");
            onComplete.run();
            return null;
        }).when(aiService).streamAnalyze(anyString(), anyList(), any(), any(), any());
        when(messageRepo.save(any())).thenReturn(null);

        ChatRequest req = new ChatRequest();
        req.setContent("hi");
        req.setTitle("test");
        // 用反射调 streamResponse（private 方法），通过 startConversation 间接调
        when(conversationRepo.save(any())).thenReturn(c);
        chatService.startConversation(req, testUser);

        // 等异步完成
        Thread.sleep(200);

        // 验证 ASSISTANT 消息被保存（内容是 "hello world"）
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepo, atLeast(1)).save(captor.capture());
        List<Message> saved = captor.getAllValues();
        Message aiMsg = saved.stream()
                .filter(m -> m.getRole().name().equals("ASSISTANT"))
                .findFirst().orElse(null);
        assertNotNull(aiMsg, "应保存 ASSISTANT 消息");
        assertEquals("hello world", aiMsg.getContent());
    }

    @Test
    @DisplayName("AI 失败时不保存 ASSISTANT 消息")
    void shouldNotSaveOnError() throws Exception {
        Conversation c = new Conversation("test", testUser);
        c.setId(1L);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var onError = (java.util.function.Consumer<Throwable>) inv.getArgument(4);
            onError.accept(new RuntimeException("AI 调用失败"));
            return null;
        }).when(aiService).streamAnalyze(anyString(), anyList(), any(), any(), any());
        when(conversationRepo.save(any())).thenReturn(c);

        ChatRequest req = new ChatRequest();
        req.setContent("hi");
        chatService.startConversation(req, testUser);
        Thread.sleep(200);

        // 不应该保存 ASSISTANT 消息（只保存了 USER 消息）
        verify(messageRepo, never()).save(argThat(m -> m.getRole() != null
                && m.getRole().name().equals("ASSISTANT")));
    }
}
