package com.example.demo.service;

import com.example.demo.dto.AIResult;
import com.example.demo.dto.ChatMessage;
import com.example.demo.dto.ToolCall;
import com.example.demo.exception.AIServiceException;
import com.example.demo.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Mock AI 服务 —— 不调用真实 API，返回固定模板结果
 * <p>
 * 当 ai.provider=mock 或未配置 ai.provider 时生效。
 * 使用有界线程池 chatStreamExecutor，不再直接 new Thread。
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAIService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(MockAIService.class);

    private final ThreadPoolTaskExecutor chatStreamExecutor;

    public MockAIService(@Qualifier("chatStreamExecutor") ThreadPoolTaskExecutor chatStreamExecutor) {
        this.chatStreamExecutor = chatStreamExecutor;
    }

    @Override
    public AIResult analyze(String inputText, String taskType) {
        log.info("Mock AI 分析开始: taskType={}, inputText={}", taskType, inputText);
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        AIResult result = new AIResult();
        result.setSummary("这是对输入内容的模拟摘要：\"" + inputText + "\" 已完成初步分析。");
        result.setConclusion("模拟分析结论：输入内容属于" + taskType + "范畴，未发现明显异常。");
        result.setSuggestion("后续可将 ai.provider 切换为 openai，接入真实大模型 API。");
        result.setInputTokens(0);
        result.setOutputTokens(0);
        result.setModelName("mock");

        log.info("Mock AI 分析完成: tokensUsed=0, model=mock");
        return result;
    }

    @Override
    public ChatMessage chatCompletion(List<ChatMessage> messages, List<Map<String, Object>> toolSchemas) {
        log.info("Mock chat 开始");
        // 确定性：若已存在 tool 结果消息 → 直接给最终文本；否则按 user 文本关键词挑一个工具（空参数）
        boolean hasToolResult = messages.stream().anyMatch(m -> "tool".equals(m.role()));
        if (hasToolResult) {
            return ChatMessage.assistant("（模拟）根据工具结果给出最终回答。", null);
        }
        String userText = messages.stream()
                .filter(m -> "user".equals(m.role()))
                .map(ChatMessage::content)
                .reduce("", (a, b) -> a + (a.isEmpty() ? "" : " ") + b);
        String toolName = pickTool(userText, toolSchemas);
        if (toolName == null) {
            return ChatMessage.assistant("（模拟）无需调用工具的直接回答。", null);
        }
        List<ToolCall> calls = new ArrayList<>();
        calls.add(new ToolCall("mock-call-" + toolName, toolName, Map.of()));
        return ChatMessage.assistant(null, calls);
    }

    private String pickTool(String userText, List<Map<String, Object>> toolSchemas) {
        if (toolSchemas == null || toolSchemas.isEmpty()) return null;
        String lower = userText == null ? "" : userText.toLowerCase();
        if (lower.contains("检索") || lower.contains("知识")) return "searchKnowledgeBase";
        if (lower.contains("任务") || lower.contains("task")) return "queryTask";
        if (lower.contains("文件") || lower.contains("file")) return "getFileInfo";
        return null;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        log.info("Mock AI 补全开始");
        // 确定性：从 userPrompt 中提取 [S#]（最多 3 个）作为引用，不依赖随机数
        List<String> sourceIds = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[S(\\d+)\\]").matcher(userPrompt);
        while (m.find() && sourceIds.size() < 3) {
            sourceIds.add("S" + m.group(1));
        }
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("answer", "（模拟 RAG 回答）根据提供的 " + sourceIds.size() + " 份资料给出回答。");
            body.put("sourceIds", sourceIds);
            return new ObjectMapper().writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Mock JSON 序列化失败", e);
        }
    }

    @Override
    public String getProvider() { return "mock"; }

    @Override
    public String getPromptVersion() { return "mock-v1"; }

    @Override
    public String getModelName() { return "mock"; }

    @Override
    public void streamAnalyze(String systemPrompt, List<Message> history,
                              BooleanSupplier isCancelled,
                              Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError,
                              AtomicInteger retryCountOut) {
        // Mock 不重试，retryCountOut 保持默认 0
        String fullResponse = "这是 Mock 流式回复。根据你的" + history.size() + "条历史消息，" +
                "这是一个模拟的多轮对话响应。实际部署时将返回真实 AI 流式输出。";

        try {
            chatStreamExecutor.execute(() -> {
                retryCountOut.set(0); // Mock 不重试，固定写 0
                try {
                    for (char c : fullResponse.toCharArray()) {
                        if (isCancelled.getAsBoolean()) {
                            log.info("Mock 流式被取消");
                            return;
                        }
                        onChunk.accept(String.valueOf(c));
                        Thread.sleep(30); // 模拟 30ms/字
                    }
                    onComplete.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (!isCancelled.getAsBoolean()) {
                        onError.accept(new AIServiceException("cancelled", "流式任务被中断", e));
                    }
                } catch (Exception e) {
                    if (!isCancelled.getAsBoolean()) {
                        onError.accept(e);
                    }
                }
            });
        } catch (Exception e) {
            // 线程池队列满 / 拒绝
            log.warn("Mock 流式线程池拒绝任务", e);
            onError.accept(new AIServiceException("overloaded", "AI 服务繁忙，请稍后重试", e));
        }
    }
}
