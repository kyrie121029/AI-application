package com.example.demo.service;

import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.ConversationResponse;
import com.example.demo.dto.MessageResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 对话服务 —— 管理多轮对话 + SSE 流式输出
 * <p>
 * 设计要点：
 *   1. 所有数据库写入委托给 ChatPersistenceService（保证 @Transactional 经代理生效）
 *   2. 流式方法不被 @Transactional 包裹（避免长事务占用数据库连接）
 *   3. 异步回调只传 conversationId，不跨线程传递 JPA Entity
 *   4. 上下文按 Token 预算选择（保留最近窗口 + 预算截断 + 时间升序）
 *   5. AtomicBoolean 保证流式只进入一次终态（onComplete/onError/超时/取消互斥）
 *   6. 客户端断开通过 terminated 标志传播给 AI 实现（isCancelled）
 *   7. 流式完成/失败写入 AIUsageLog
 *   8. requestId 幂等防重复提交
 *   9. 同一会话并发消息用条带锁串行化保存+读上下文（固定锁数组，避免 Map 无限增长）
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final ChatPersistenceService persistenceService;
    private final AIService aiService;
    private final AIUsageLogRepository usageLogRepository;
    private final TokenEstimator tokenEstimator;

    @Value("${ai.chat.system-prompt:你是一个有用的AI助手。}")
    private String chatSystemPrompt;

    @Value("${ai.chat.prompt-version:chat-system-v1}")
    private String chatPromptVersion;

    /** 上下文总预算（含 system prompt、当前用户消息与历史消息；模型输出从中预留） */
    @Value("${ai.chat.max-input-tokens:4000}")
    private int maxInputTokens;

    /** 模型最大输出 Token 预留 */
    @Value("${ai.chat.max-output-tokens:1000}")
    private int maxOutputTokens;

    /** 会话锁条带：固定 256 个锁对象，同一会话始终映射到同一把锁。
     *  用固定数组替代 ConcurrentHashMap，避免 conversationId 无限增长导致 Map 长期积累；
     *  代价是少数不同会话可能共享同一把锁（仅额外等待，不影响正确性）。 */
    private static final int LOCK_STRIPES = 256;
    private final Object[] lockStripes;

    public ChatService(ConversationRepository conversationRepo,
                       MessageRepository messageRepo,
                       ChatPersistenceService persistenceService,
                       AIService aiService,
                       AIUsageLogRepository usageLogRepository,
                       TokenEstimator tokenEstimator) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.persistenceService = persistenceService;
        this.aiService = aiService;
        this.usageLogRepository = usageLogRepository;
        this.tokenEstimator = tokenEstimator;
        this.lockStripes = new Object[LOCK_STRIPES];
        for (int i = 0; i < lockStripes.length; i++) {
            lockStripes[i] = new Object();
        }
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
        Conversation conv = persistenceService.findConversation(convId);
        checkOwnership(conv, user, "无权访问该对话");
        return messageRepo.findByConversationIdOrderByCreatedAtAsc(convId)
                .stream().map(MessageResponse::from).collect(Collectors.toList());
    }

    /** 删除对话 */
    public void deleteConversation(Long convId, User user) {
        Conversation conv = persistenceService.findConversation(convId);
        checkOwnership(conv, user, "无权删除该对话");
        persistenceService.deleteConversation(convId);
        log.info("对话已删除: convId={}, userId={}", convId, user.getId());
    }

    /** 新建对话：保存会话+首条消息 → 加载上下文（含首条消息）→ 流式返回 */
    public SseEmitter startConversation(ChatRequest request, User user) {
        String title = (request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle()
                : request.getContent().substring(0, Math.min(30, request.getContent().length()));
        Conversation conv = persistenceService.createConversation(title, user, request.getContent());
        List<Message> context = loadContext(conv.getId());
        return streamResponse(conv.getId(), user.getId(), context);
    }

    /** 继续对话：校验归属 + 幂等 → 保存用户消息 → 加载 Token 预算上下文 → 流式返回 */
    public SseEmitter continueConversation(Long convId, ChatRequest request, User user) {
        List<Message> context;
        synchronized (lockFor(convId)) {
            Conversation conv = persistenceService.findConversation(convId);
            checkOwnership(conv, user, "无权访问该对话");

            // 幂等：同一会话同一 requestId 只执行一次
            if (request.getRequestId() != null && request.getRequestId().isBlank()) {
                throw new DuplicateRequestException("requestId 不能为空字符串");
            }
            if (request.getRequestId() != null
                    && persistenceService.userMessageExists(convId, request.getRequestId())) {
                throw new DuplicateRequestException("请求已处理过: requestId=" + request.getRequestId());
            }

            persistenceService.saveUserMessage(convId, request.getContent(), request.getRequestId());
            context = loadContext(convId);
        }
        return streamResponse(convId, user.getId(), context);
    }

    // ==================== 私有方法 ====================

    private Object lockFor(Long conversationId) {
        int index = (int) Math.floorMod(conversationId, lockStripes.length);
        return lockStripes[index];
    }

    /**
     * 加载上下文：候选最近 20 条（时间倒序）→ Token 预算截断 → 恢复时间升序。
     * 预算语义：
     *   maxInputTokens 是上下文总预算（含 system prompt、当前用户消息与历史消息），
     *   模型输出从中预留 maxOutputTokens；
     *   历史消息预算 = maxInputTokens - system - 当前用户 - 预留输出。
     * 当前用户消息（最新一条）始终保留；历史消息按 recent-first 挑选，预算不足即停止。
     */
    private List<Message> loadContext(Long conversationId) {
        List<Message> recentDesc = messageRepo.findTop20ByConversationIdOrderByCreatedAtDesc(conversationId);
        if (recentDesc.isEmpty()) return List.of();

        Message current = recentDesc.get(0); // 最新 = 当前用户消息，始终保留
        int systemTokens = tokenEstimator.estimateTokens(chatSystemPrompt);
        int currentTokens = tokenEstimator.estimateTokens(current.getContent());

        // 边界校验：system + 当前用户消息 + 预留输出 已超过总预算 → 不构造超预算请求，不静默截断用户输入
        if (systemTokens + currentTokens + maxOutputTokens > maxInputTokens) {
            throw new InputTooLongException("当前消息过长（约 " + currentTokens
                    + " token，含 System 与输出预留后超出上下文预算 " + maxInputTokens + "）");
        }

        int historyBudget = maxInputTokens - systemTokens - currentTokens - maxOutputTokens;

        List<Message> selected = new ArrayList<>();
        selected.add(current);
        int used = 0; // 只累计历史消息 token（当前消息已在预算中扣减，避免重复计算）
        for (int i = 1; i < recentDesc.size(); i++) {
            Message m = recentDesc.get(i);
            int t = tokenEstimator.estimateTokens(m.getContent());
            if (used + t > historyBudget) break;
            selected.add(m);
            used += t;
        }
        Collections.reverse(selected); // 旧 → 新
        log.info("上下文选择: convId={}, selected={}条, historyTokens={}, historyBudget={}",
                conversationId, selected.size(), used, historyBudget);
        return selected;
    }

    private void checkOwnership(Conversation conv, User user, String message) {
        if (!conv.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException(message);
        }
    }

    /**
     * 流式调用 AI，通过 SseEmitter 逐块推送。
     * terminated 同时作为 isCancelled 传给 AI 实现，客户端断开即停止。
     */
    private SseEmitter streamResponse(Long conversationId, Long userId, List<Message> history) {
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean terminated = new AtomicBoolean(false);
        AtomicInteger retryCount = new AtomicInteger(0); // 输出给 AI 实现记录真实重试次数
        StringBuilder fullResponse = new StringBuilder();
        long[] firstTokenTime = {0};
        long startTime = System.currentTimeMillis();

        emitter.onTimeout(() -> {
            if (terminated.compareAndSet(false, true)) {
                long elapsed = System.currentTimeMillis() - startTime;
                log.warn("SSE 超时: convId={}, elapsed={}ms", conversationId, elapsed);
                saveStreamUsageLog(userId, conversationId, elapsed, firstTokenLatencyMs(firstTokenTime, startTime),
                        retryCount.get(), "timeout", false, firstTokenTime[0] == 0);
                emitter.complete();
            }
        });
        emitter.onError(ex -> onEmitterError(ex, terminated, userId, conversationId,
                startTime, firstTokenTime, retryCount));
        emitter.onCompletion(() -> log.info("SSE 完成: convId={}", conversationId));

        aiService.streamAnalyze(chatSystemPrompt, history, terminated::get,
                chunk -> { // onChunk
                    if (terminated.get()) return;
                    try {
                        if (firstTokenTime[0] == 0) {
                            firstTokenTime[0] = System.currentTimeMillis();
                            log.info("首 Token 延迟: convId={}, {}ms", conversationId,
                                    firstTokenTime[0] - startTime);
                        }
                        emitter.send(SseEmitter.event().data(chunk));
                        fullResponse.append(chunk);
                    } catch (IOException e) {
                        // 客户端断开：标记终态，取消 AI 任务
                        if (terminated.compareAndSet(false, true)) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            log.error("SSE 发送失败(客户端断开): convId={}", conversationId, e);
                            saveStreamUsageLog(userId, conversationId, elapsed,
                                    firstTokenLatencyMs(firstTokenTime, startTime), retryCount.get(),
                                    "cancelled", false, firstTokenTime[0] == 0);
                            emitter.complete();
                        }
                    }
                },
                () -> completeStreamSuccessfully(emitter, terminated, userId, conversationId,
                        startTime, firstTokenTime, retryCount, fullResponse.toString()),
                error -> { // onError
                    if (terminated.compareAndSet(false, true)) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        String errorType = error instanceof AIServiceException
                                ? ((AIServiceException) error).getErrorType() : "internal";
                        log.error("AI 流式失败: convId={}, errorType={}", conversationId, errorType, error);
                        saveStreamUsageLog(userId, conversationId, elapsed,
                                firstTokenLatencyMs(firstTokenTime, startTime), retryCount.get(),
                                errorType, false, firstTokenTime[0] == 0);
                        sendStructuredError(emitter, errorType, isRetryableErrorType(errorType));
                        emitter.complete();
                    }
                },
                retryCount);

        return emitter;
    }

    /**
     * onComplete 执行体（拆出便于单测）——三类失败分开处理：
     *   1. Assistant 持久化失败 → persistence 错误（模型已完成，不能当作模型失败）
     *   2. UsageLog 保存失败 → 只记日志，不影响成功响应（saveStreamUsageLog 内部已吞异常）
     *   3. done 事件发送失败 → 视为客户端断开/发送失败，不得误报 persistence，
     *      也不得再写一条矛盾的 UsageLog（成功日志此时已落库，是真实状态）
     *
     * @return true 表示完整走完成功流程
     */
    boolean completeStreamSuccessfully(SseEmitter emitter, AtomicBoolean terminated, Long userId,
                                      Long conversationId, long startTime, long[] firstTokenTime,
                                      AtomicInteger retryCount, String fullResponse) {
        if (!terminated.compareAndSet(false, true)) {
            return false; // 已有其他终态抢先
        }
        long elapsed = System.currentTimeMillis() - startTime;
        long firstToken = firstTokenLatencyMs(firstTokenTime, startTime);

        // 1) Assistant 持久化 —— 失败则整体按 persistence 失败处理
        try {
            persistenceService.saveAssistantMessage(conversationId, fullResponse);
        } catch (Exception e) {
            log.error("Assistant 保存失败: convId={}", conversationId, e);
            saveStreamUsageLog(userId, conversationId, elapsed, firstToken,
                    retryCount.get(), "persistence", false, firstTokenTime[0] == 0);
            sendStructuredError(emitter, "persistence", false);
            emitter.complete();
            return false;
        }

        // 2) 成功日志（独立于 done 发送：Assistant 已落库即是成功）
        saveStreamUsageLog(userId, conversationId, elapsed, firstToken,
                retryCount.get(), null, true, false);

        // 3) done 事件发送 —— 失败视为客户端断开，不误报 persistence，不写第二条日志
        try {
            emitter.send(SseEmitter.event().name("done").data(""));
        } catch (Exception e) {
            log.warn("done 发送失败(客户端已断开): convId={}", conversationId, e);
            return false;
        }
        emitter.complete();
        log.info("对话完成: convId={}, len={}, elapsed={}ms, firstToken={}ms",
                conversationId, fullResponse.length(), elapsed, firstToken);
        return true;
    }

    /**
     * emitter 连接级异常回调（客户端断开等）——首次到达终态时记录 cancelled 失败日志，
     * 避免该场景下日志缺失。
     */
    boolean onEmitterError(Throwable ex, AtomicBoolean terminated, Long userId, Long conversationId,
                           long startTime, long[] firstTokenTime, AtomicInteger retryCount) {
        if (terminated.compareAndSet(false, true)) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.warn("SSE 异常(连接中断): convId={}", conversationId, ex);
            saveStreamUsageLog(userId, conversationId, elapsed,
                    firstTokenLatencyMs(firstTokenTime, startTime), retryCount.get(),
                    "cancelled", false, firstTokenTime[0] == 0);
            return true;
        }
        return false;
    }

    private long firstTokenLatencyMs(long[] firstTokenTime, long startTime) {
        return firstTokenTime[0] > 0 ? firstTokenTime[0] - startTime : 0;
    }

    /** 发送结构化 SSE error 事件；若连接已断开则只记日志（发送失败不影响调用方流程） */
    private void sendStructuredError(SseEmitter emitter, String errorType, boolean retryable) {
        try {
            String payload = "{\"errorType\":\"" + errorType + "\","
                    + "\"message\":\"" + errorMessage(errorType) + "\","
                    + "\"retryable\":" + retryable + "}";
            emitter.send(SseEmitter.event().name("error").data(payload));
        } catch (Exception e) {
            log.warn("SSE 连接已断开，无法发送 error 事件", e);
        }
    }

    private String errorMessage(String errorType) {
        return switch (errorType) {
            case "auth" -> "模型服务认证失败";
            case "rate_limit" -> "模型服务繁忙，请稍后重试";
            case "timeout" -> "模型响应超时";
            case "upstream", "server" -> "模型服务异常，请稍后重试";
            case "bad_request" -> "请求参数错误";
            case "cancelled" -> "请求已取消";
            case "overloaded" -> "AI 服务繁忙，请稍后重试";
            case "persistence" -> "保存对话记录失败";
            case "network" -> "网络连接失败";
            default -> "AI 服务异常";
        };
    }

    private boolean isRetryableErrorType(String errorType) {
        return "timeout".equals(errorType) || "rate_limit".equals(errorType)
                || "upstream".equals(errorType) || "server".equals(errorType)
                || "network".equals(errorType);
    }

    /** 流式调用结束后写入 AIUsageLog（独立保存，失败不影响主流程） */
    private void saveStreamUsageLog(Long userId, Long conversationId, long elapsedMs,
                                    long firstTokenLatencyMs, int retryCount,
                                    String errorType, boolean success, boolean failedBeforeFirstToken) {
        try {
            AIUsageLog usageLog;
            if (success) {
                usageLog = new AIUsageLog(userId, conversationId, aiService.getProvider(),
                        aiService.getModelName(), elapsedMs, firstTokenLatencyMs, retryCount,
                        chatPromptVersion);
            } else {
                usageLog = new AIUsageLog(userId, conversationId, aiService.getProvider(),
                        aiService.getModelName(), elapsedMs, firstTokenLatencyMs, retryCount,
                        failedBeforeFirstToken, errorType, chatPromptVersion);
            }
            usageLogRepository.save(usageLog);
        } catch (Exception ex) {
            log.error("保存 AI 流式日志失败: convId={}", conversationId, ex);
        }
    }
}
