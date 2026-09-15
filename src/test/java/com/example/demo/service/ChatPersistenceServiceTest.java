package com.example.demo.service;

import com.example.demo.enums.MessageRole;
import com.example.demo.exception.ConversationNotFoundException;
import com.example.demo.model.Conversation;
import com.example.demo.model.User;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * ChatPersistenceService 单元测试
 * <p>
 * 注意：纯 Mockito 测试无法验证真实 @Transactional 回滚行为，
 * 这里验证方法内部的操作顺序和失败传播——真正的回滚由 Spring 事务代理保证。
 */
@ExtendWith(MockitoExtension.class)
class ChatPersistenceServiceTest {

    @Mock private ConversationRepository conversationRepo;
    @Mock private MessageRepository messageRepo;
    @InjectMocks private ChatPersistenceService persistenceService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User(1L, "alice", "pwd");
    }

    @Test
    @DisplayName("创建会话：先存 Conversation 再存首条 USER 消息（同一事务内）")
    void createConversationSavesConversationThenMessage() {
        Conversation c = new Conversation("标题", testUser);
        when(conversationRepo.save(any())).thenReturn(c);
        when(messageRepo.save(any())).thenReturn(null);

        Conversation result = persistenceService.createConversation("标题", testUser, "首条消息");

        assertNotNull(result);
        InOrder inOrder = inOrder(conversationRepo, messageRepo);
        inOrder.verify(conversationRepo).save(any(Conversation.class));
        inOrder.verify(messageRepo).save(argThat(m ->
                m.getRole() == MessageRole.USER && "首条消息".equals(m.getContent())));
    }

    @Test
    @DisplayName("创建会话：首条消息保存失败时异常上抛（事务将整体回滚）")
    void createConversationPropagatesMessageFailure() {
        Conversation c = new Conversation("标题", testUser);
        when(conversationRepo.save(any())).thenReturn(c);
        when(messageRepo.save(any())).thenThrow(new RuntimeException("数据库写入失败"));

        assertThrows(RuntimeException.class,
                () -> persistenceService.createConversation("标题", testUser, "首条消息"));
        // 异常必须向上传播，Spring 才会回滚 Conversation 的写入
        verify(conversationRepo).save(any());
        verify(messageRepo).save(any());
    }

    @Test
    @DisplayName("保存用户消息：用 getReferenceById 获取会话，保存 USER 消息")
    void saveUserMessageUsesReference() {
        when(conversationRepo.getReferenceById(1L)).thenReturn(new Conversation("t", testUser));
        when(messageRepo.save(any())).thenReturn(null);

        persistenceService.saveUserMessage(1L, "hello", null);

        verify(conversationRepo).getReferenceById(1L);
        verify(messageRepo).save(argThat(m ->
                m.getRole() == MessageRole.USER && "hello".equals(m.getContent())));
    }

    @Test
    @DisplayName("保存用户消息：requestId 会写入 Message")
    void saveUserMessageStoresRequestId() {
        when(conversationRepo.getReferenceById(1L)).thenReturn(new Conversation("t", testUser));
        when(messageRepo.save(any())).thenReturn(null);

        persistenceService.saveUserMessage(1L, "hello", "req-123");

        verify(messageRepo).save(argThat(m ->
                "req-123".equals(m.getRequestId())));
    }

    @Test
    @DisplayName("幂等检查：委托给 repository")
    void userMessageExistsDelegates() {
        when(messageRepo.existsByConversationIdAndRequestId(1L, "req-1")).thenReturn(true);
        assertTrue(persistenceService.userMessageExists(1L, "req-1"));
        assertFalse(persistenceService.userMessageExists(1L, "req-2"));
    }

    @Test
    @DisplayName("保存 AI 回复：用 getReferenceById 获取会话，保存 ASSISTANT 消息")
    void saveAssistantMessageUsesReference() {
        when(conversationRepo.getReferenceById(1L)).thenReturn(new Conversation("t", testUser));
        when(messageRepo.save(any())).thenReturn(null);

        persistenceService.saveAssistantMessage(1L, "reply");

        verify(conversationRepo).getReferenceById(1L);
        verify(messageRepo).save(argThat(m ->
                m.getRole() == MessageRole.ASSISTANT && "reply".equals(m.getContent())));
    }

    @Test
    @DisplayName("查找不存在的会话 → ConversationNotFoundException")
    void findConversationThrowsWhenMissing() {
        when(conversationRepo.findByIdWithUser(99L)).thenReturn(Optional.empty());
        assertThrows(ConversationNotFoundException.class,
                () -> persistenceService.findConversation(99L));
    }

    @Test
    @DisplayName("删除会话：级联删除消息和会话")
    void deleteConversationDeletesMessagesAndConversation() {
        Conversation c = new Conversation("t", testUser);
        when(conversationRepo.findById(1L)).thenReturn(Optional.of(c));
        when(messageRepo.findByConversationIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        persistenceService.deleteConversation(1L);

        verify(conversationRepo).findById(1L);
        verify(messageRepo).deleteAll(any());
        verify(conversationRepo).delete(c);
    }
}
