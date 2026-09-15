package com.example.demo.service;

import com.example.demo.enums.MessageRole;
import com.example.demo.exception.ConversationNotFoundException;
import com.example.demo.model.Conversation;
import com.example.demo.model.Message;
import com.example.demo.model.User;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对话持久化服务 —— 负责所有对话/消息的数据库写入
 * <p>
 * 为什么独立出来：
 * ChatService 中 self-invocation 会导致 private 方法上的 @Transactional 不生效，
 * 因此所有事务性写入方法必须是 public 且由独立 Bean 通过代理调用。
 * <p>
 * 事务边界：
 *   创建会话 + 首条消息    → 同一事务（REQUIRES_NEW）
 *   保存 USER 消息         → 独立短事务（REQUIRES_NEW）
 *   保存 ASSISTANT 消息    → 独立短事务（REQUIRES_NEW）
 *   删除会话 + 消息        → 同一事务
 * <p>
 * 模型流式调用本身不在任何事务中，避免长时间占用数据库连接。
 */
@Service
public class ChatPersistenceService {

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;

    public ChatPersistenceService(ConversationRepository conversationRepo,
                                  MessageRepository messageRepo) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
    }

    /** 创建会话 + 首条 USER 消息（同一事务，任一步失败整体回滚） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Conversation createConversation(String title, User user, String content) {
        Conversation conv = conversationRepo.save(new Conversation(title, user));
        messageRepo.save(new Message(conv, MessageRole.USER, content));
        return conv;
    }

    /** 保存用户消息（独立短事务），可选记录 requestId 用于幂等 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveUserMessage(Long conversationId, String content, String requestId) {
        Conversation conv = conversationRepo.getReferenceById(conversationId);
        Message msg = new Message(conv, MessageRole.USER, content);
        msg.setRequestId(requestId);
        messageRepo.save(msg);
    }

    /** 保存 AI 回复（独立短事务，供流式 onComplete 回调调用） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAssistantMessage(Long conversationId, String content) {
        Conversation conv = conversationRepo.getReferenceById(conversationId);
        messageRepo.save(new Message(conv, MessageRole.ASSISTANT, content));
    }

    /** 幂等检查：同一会话同一 requestId 是否已有 USER 消息 */
    @Transactional(readOnly = true)
    public boolean userMessageExists(Long conversationId, String requestId) {
        return messageRepo.existsByConversationIdAndRequestId(conversationId, requestId);
    }

    /** 查询会话（带 user JOIN FETCH，避免懒加载问题） */
    @Transactional(readOnly = true)
    public Conversation findConversation(Long conversationId) {
        return conversationRepo.findByIdWithUser(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }

    /** 删除会话及其所有消息（同一事务） */
    @Transactional
    public void deleteConversation(Long conversationId) {
        Conversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        messageRepo.deleteAll(messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId));
        conversationRepo.delete(conv);
    }
}
