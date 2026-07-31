package com.example.demo.service;

import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.ConversationResponse;
import com.example.demo.dto.MessageResponse;
import com.example.demo.enums.MessageRole;
import com.example.demo.model.Conversation;
import com.example.demo.model.Message;
import com.example.demo.model.User;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话服务 —— 管理多轮对话 + SSE 流式输出
 * <p>
 * 改进：
 *   1. 流式方法不再被 @Transactional 包裹（避免长事务）
 *   2. getMessages 加了归属校验
 *   3. 上下文窗口限制（最近 20 条）
 *   4. SseEmitter 生命周期回调
 *   5. System Prompt 从配置读取
 *   6. 首 Token 延迟追踪
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_CONTEXT_MESSAGES = 20; // 最近 20 条消息

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final AIService aiService;

    @Value("${ai.chat.system-prompt:你是一个有用的AI助手。}")
    private String chatSystemPrompt;

    public ChatService(ConversationRepository conversationRepo,
                       MessageRepository messageRepo,
                       AIService aiService) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.aiService = aiService;
    }

    /** 对话列表 */
    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(User user) {
        return conversationRepo.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(ConversationResponse::from).collect(Collectors.toList());
    }

    /** 对话历史（校验归属） */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(Long convId, User user) {
        Conversation conv = conversationRepo.findById(convId)
                .orElseThrow(() -> new RuntimeException("对话不存在: id=" + convId));
        if (!conv.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("无权访问该对话");
        }
        return messageRepo.findByConversationIdOrderByCreatedAtAsc(convId)
                .stream().map(MessageResponse::from).collect(Collectors.toList());
    }

    /** 删除对话 */
    @Transactional
    public void deleteConversation(Long convId, User user) {
        Conversation conv = conversationRepo.findById(convId)
                .orElseThrow(() -> new RuntimeException("对话不存在: id=" + convId));
        if (!conv.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("无权删除该对话");
        }
        messageRepo.deleteAll(messageRepo.findByConversationIdOrderByCreatedAtAsc(convId));
        conversationRepo.delete(conv);
        log.info("对话已删除: convId={}, userId={}", convId, user.getId());
    }

    /** 新建对话（事务内保存 → 事务外流式） */
    public SseEmitter startConversation(ChatRequest request, User user) {
        String title = (request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle()
                : request.getContent().substring(0, Math.min(30, request.getContent().length()));
        Conversation conv = saveConversation(title, user, request.getContent());
        return streamResponse(conv, List.of());
    }

    /** 继续对话（事务内保存 → 事务外流式） */
    public SseEmitter continueConversation(Long convId, ChatRequest request, User user) {
        Conversation conv = conversationRepo.findById(convId)
                .orElseThrow(() -> new RuntimeException("对话不存在: id=" + convId));
        if (!conv.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("无权访问该对话");
        }
        // 保存用户消息（独立短事务）
        saveUserMessage(conv, request.getContent());

        // 读历史 + 截断上下文
        List<Message> fullHistory = messageRepo.findByConversationIdOrderByCreatedAtAsc(convId);
        List<Message> context = fullHistory.size() > MAX_CONTEXT_MESSAGES
                ? fullHistory.subList(fullHistory.size() - MAX_CONTEXT_MESSAGES, fullHistory.size())
                : fullHistory;

        return streamResponse(conv, context);
    }

    // ==================== 私有方法 ====================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private Conversation saveConversation(String title, User user, String content) {
        Conversation conv = conversationRepo.save(new Conversation(title, user));
        messageRepo.save(new Message(conv, MessageRole.USER, content));
        return conv;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveUserMessage(Conversation conv, String content) {
        messageRepo.save(new Message(conv, MessageRole.USER, content));
    }

    /** 流式调用 AI，通过 SseEmitter 逐块推送 */
    private SseEmitter streamResponse(Conversation conv, List<Message> history) {
        SseEmitter emitter = new SseEmitter(120_000L);
        StringBuilder fullResponse = new StringBuilder();
        long[] firstTokenTime = {0};
        long startTime = System.currentTimeMillis();

        // 客户端断开/超时时清理
        emitter.onTimeout(() -> log.warn("SSE 超时: convId={}", conv.getId()));
        emitter.onError(ex -> log.warn("SSE 异常: convId={}", conv.getId(), ex));
        emitter.onCompletion(() -> log.info("SSE 完成: convId={}", conv.getId()));

        aiService.streamAnalyze(chatSystemPrompt, history,
                chunk -> { // onChunk
                    try {
                        if (firstTokenTime[0] == 0) {
                            firstTokenTime[0] = System.currentTimeMillis();
                            log.info("首 Token 延迟: convId={}, {}ms", conv.getId(),
                                    firstTokenTime[0] - startTime);
                        }
                        emitter.send(SseEmitter.event().data(chunk));
                        fullResponse.append(chunk);
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                () -> { // onComplete
                    long elapsed = System.currentTimeMillis() - startTime;
                    try {
                        // 独立短事务保存 AI 回复
                        saveAssistantMessage(conv, fullResponse.toString());
                        emitter.send(SseEmitter.event().name("done").data(""));
                        emitter.complete();
                        log.info("对话完成: convId={}, len={}, elapsed={}ms, firstToken={}ms",
                                conv.getId(), fullResponse.length(), elapsed,
                                firstTokenTime[0] > 0 ? firstTokenTime[0] - startTime : -1);
                    } catch (Exception e) {
                        log.error("保存 AI 回复失败: convId={}", conv.getId(), e);
                        emitter.completeWithError(e);
                    }
                },
                error -> { // onError
                    log.error("AI 流式失败: convId={}", conv.getId(), error);
                    emitter.completeWithError(error);
                });

        return emitter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveAssistantMessage(Conversation conv, String content) {
        messageRepo.save(new Message(conv, MessageRole.ASSISTANT, content));
    }
}
