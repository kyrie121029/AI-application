package com.example.demo.service;

import com.example.demo.config.AIProperties;
import com.example.demo.config.PromptTemplate;
import com.example.demo.dto.AIResult;
import com.example.demo.dto.ChatMessage;
import com.example.demo.exception.AIResponseParseException;
import com.example.demo.exception.AIServiceException;
import com.example.demo.model.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * OpenAI 兼容 API 服务 —— 调用真实大模型
 * <p>
 * 改进：
 *   1. AIProperties 统一配置（替代 @Value）
 *   2. PromptTemplate 变量替换（替代 + 拼接）
 *   3. 同步路径：有限重试 + 指数退避
 *   4. 流式路径：HTTP 状态码分类 + 首 chunk 前有限重试 + 客户端取消
 *   5. 有界线程池替代 new Thread
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAIAIService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIAIService.class);
    private static final int[] RETRY_DELAYS_MS = {1000, 2000, 4000}; // 1s, 2s, 4s

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AIProperties props;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final ThreadPoolTaskExecutor chatStreamExecutor;

    public OpenAIAIService(AIProperties props,
                           @Qualifier("chatStreamExecutor") ThreadPoolTaskExecutor chatStreamExecutor) {
        this.props = props;
        this.chatStreamExecutor = chatStreamExecutor;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeout());
        factory.setReadTimeout(props.getReadTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.systemPromptTemplate = new PromptTemplate(
                props.getOpenai().getSystemPromptVersion(),
                props.getOpenai().getSystemPrompt());
        this.userPromptTemplate = new PromptTemplate(
                props.getOpenai().getSystemPromptVersion(),
                props.getOpenai().getUserPrompt());
    }

    // ==================== 同步分析（Phase 4） ====================

    @Override
    public AIResult analyze(String inputText, String taskType) {
        String userPrompt = userPromptTemplate.render(
                Map.of("inputText", inputText, "taskType", taskType));

        log.info("OpenAI 调用: model={}, promptVersion={}, inputLength={}",
                props.getOpenai().getModel(), systemPromptTemplate.getVersion(), inputText.length());

        Map<String, Object> requestBody = Map.of(
                "model", props.getOpenai().getModel(),
                "messages", new Object[]{
                        Map.of("role", "system", "content", systemPromptTemplate.getTemplate()),
                        Map.of("role", "user", "content", userPrompt)
                },
                "temperature", 0.7,
                "max_tokens", 1000
        );

        long startTime = System.currentTimeMillis();
        String responseBody = callWithRetry(requestBody);
        long elapsed = System.currentTimeMillis() - startTime;

        AIResult result = parseResponse(responseBody);
        log.info("OpenAI 完成: model={}, inputTokens={}, outputTokens={}, elapsed={}ms",
                result.getModelName(), result.getInputTokens(), result.getOutputTokens(), elapsed);
        return result;
    }

    /** 同步有限重试：仅重试可恢复异常 */
    private String callWithRetry(Map<String, Object> requestBody) {
        int maxAttempts = props.getMaxRetries() + 1;
        AIServiceException lastException = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                if (attempt > 0) {
                    int delay = RETRY_DELAYS_MS[Math.min(attempt - 1, RETRY_DELAYS_MS.length - 1)];
                    log.info("AI 调用重试 {}/{}，等待 {}ms", attempt, props.getMaxRetries(), delay);
                    Thread.sleep(delay);
                }
                return doCall(requestBody);
            } catch (AIServiceException e) {
                lastException = e;
                if (!isRetryable(e.getErrorType())) {
                    throw e;
                }
                log.warn("AI 调用失败(可重试): errorType={}, attempt={}/{}",
                        e.getErrorType(), attempt + 1, maxAttempts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AIServiceException("network", "重试被中断", e);
            }
        }
        throw new AIServiceException(lastException.getErrorType(),
                "AI 调用失败，已重试 " + props.getMaxRetries() + " 次: " + lastException.getMessage());
    }

    /** 同步 HTTP 调用（单次） */
    private String doCall(Map<String, Object> requestBody) {
        try {
            return restClient.post()
                    .uri(props.getOpenai().getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + props.getOpenai().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        throw new AIServiceException(mapHttpError(code, ""), "模型服务返回 " + code);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        throw new AIServiceException(mapHttpError(code, ""), "模型服务返回 " + code);
                    })
                    .body(String.class);
        } catch (AIServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException)
                throw new AIServiceException("timeout", "AI 调用超时", e);
            if (e.getCause() instanceof ConnectException)
                throw new AIServiceException("network", "无法连接到 AI 服务", e);
            throw new AIServiceException("network", "AI 网络错误", e);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new AIServiceException("upstream", "HTTP " + e.getStatusCode().value(), e);
        }
    }

    /** 单轮补全（复用 callWithRetry 的重试/退避，不解析结构） */
    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", props.getOpenai().getModel(),
                "messages", new Object[]{
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                },
                "temperature", 0.2,
                "max_tokens", 1500
        );
        String responseBody = callWithRetry(requestBody);
        return extractContent(responseBody);
    }

    @Override
    public ChatMessage chatCompletion(List<ChatMessage> messages, List<Map<String, Object>> toolSchemas) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", props.getOpenai().getModel());
        body.put("messages", messages.stream().map(this::toWireMessage).toList());
        body.put("temperature", 0.2);
        body.put("max_tokens", 2000);
        if (toolSchemas != null && !toolSchemas.isEmpty()) {
            body.put("tools", toolSchemas);
            body.put("tool_choice", "auto");
        }
        String responseBody = callWithRetry(body);
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("choices").path(0).path("message");
            return toChatMessage(message);
        } catch (AIResponseParseException e) {
            throw e;
        } catch (Exception e) {
            throw new AIResponseParseException("chat 响应解析失败", responseBody);
        }
    }

    private Object toWireMessage(ChatMessage m) {
        Map<String, Object> wire = new java.util.HashMap<>();
        wire.put("role", m.role());
        if ("tool".equals(m.role())) {
            wire.put("tool_call_id", m.toolCallId());
            wire.put("content", m.content());
            return wire;
        }
        if ("assistant".equals(m.role()) && m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            List<Map<String, Object>> calls = new ArrayList<>();
            for (com.example.demo.dto.ToolCall call : m.toolCalls()) {
                Map<String, Object> fn = new java.util.HashMap<>();
                fn.put("name", call.name());
                fn.put("arguments", writeArguments(call.arguments()));
                Map<String, Object> tc = new java.util.HashMap<>();
                tc.put("id", call.id());
                tc.put("type", "function");
                tc.put("function", fn);
                calls.add(tc);
            }
            wire.put("content", m.content());
            wire.put("tool_calls", calls);
            return wire;
        }
        wire.put("content", m.content());
        return wire;
    }

    private ChatMessage toChatMessage(JsonNode message) {
        String content = message.path("content").isNull() ? null : message.path("content").asText();
        List<com.example.demo.dto.ToolCall> calls = new ArrayList<>();
        JsonNode callsNode = message.path("tool_calls");
        if (callsNode.isArray()) {
            for (JsonNode call : callsNode) {
                String id = call.path("id").asText();
                JsonNode fn = call.path("function");
                Map<String, Object> args = parseArguments(fn.path("arguments").asText("{}"));
                calls.add(new com.example.demo.dto.ToolCall(id, fn.path("name").asText(), args));
            }
        }
        return ChatMessage.assistant(content, calls.isEmpty() ? null : calls);
    }

    private String writeArguments(Map<String, Object> arguments) {
        try {
            return objectMapper.writeValueAsString(arguments == null ? Map.of() : arguments);
        } catch (Exception e) {
            throw new AIResponseParseException("tool 参数序列化失败", null);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String json) {
        try {
            Object v = objectMapper.readValue(json, Object.class);
            return v instanceof Map ? (Map<String, Object>) v : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 从 chat.completions 响应中提取 message.content */
    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new AIResponseParseException("AI 返回空 choices", responseBody);
            }
            String content = choices.get(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new AIResponseParseException("AI 返回空 content", responseBody);
            }
            return content;
        } catch (AIResponseParseException e) {
            throw e;
        } catch (Exception e) {
            throw new AIResponseParseException("AI 响应不是合法 JSON", responseBody);
        }
    }

    /** HTTP 状态码 → 统一的 errorType */
    static String mapHttpError(int code, String body) {
        if (code == 400) return "bad_request";
        if (code == 401 || code == 403) return "auth";
        if (code == 408) return "timeout";
        if (code == 429) return "rate_limit";
        if (code >= 500) return "upstream";
        return "client_error";
    }

    /** 判断该错误类型能否重试 */
    static boolean isRetryable(String errorType) {
        return "timeout".equals(errorType) || "rate_limit".equals(errorType)
                || "server".equals(errorType) || "upstream".equals(errorType)
                || "network".equals(errorType);
    }

    // ==================== 响应解析（不变） ====================

    private AIResult parseResponse(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new AIResponseParseException("OpenAI 响应不是合法 JSON", responseBody);
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0)
            throw new AIResponseParseException("AI 返回空 choices", responseBody);
        String content = choices.get(0).path("message").path("content").asText();
        if (content == null || content.isBlank())
            throw new AIResponseParseException("AI 返回空 content", responseBody);

        JsonNode aiJson;
        try {
            String cleaned = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            aiJson = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("AI 返回非 JSON，当纯文本处理");
            AIResult fb = new AIResult();
            fb.setSummary(content); fb.setConclusion(content);
            fb.setSuggestion("AI 未返回结构化 JSON");
            JsonNode u = root.path("usage");
            fb.setInputTokens(u.path("prompt_tokens").asInt());
            fb.setOutputTokens(u.path("completion_tokens").asInt());
            fb.setModelName(root.path("model").asText());
            return fb;
        }
        JsonNode u = root.path("usage");
        AIResult r = new AIResult();
        r.setSummary(aiJson.path("summary").asText());
        r.setConclusion(aiJson.path("conclusion").asText());
        r.setSuggestion(aiJson.path("suggestion").asText());
        r.setInputTokens(u.path("prompt_tokens").asInt());
        r.setOutputTokens(u.path("completion_tokens").asInt());
        r.setModelName(root.path("model").asText());
        return r;
    }

    @Override
    public String getProvider() { return "openai"; }

    @Override
    public String getPromptVersion() { return systemPromptTemplate.getVersion(); }

    @Override
    public String getModelName() { return props.getOpenai().getModel(); }

    // ==================== 流式对话（Phase 5） ====================

    @Override
    public void streamAnalyze(String systemPrompt, List<Message> history,
                              BooleanSupplier isCancelled,
                              Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError,
                              AtomicInteger retryCountOut) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (Message msg : history) {
            messages.add(Map.of("role", msg.getRole().name().toLowerCase(), "content", msg.getContent()));
        }

        Map<String, Object> requestBody = Map.of(
                "model", props.getOpenai().getModel(),
                "messages", messages.toArray(),
                "stream", true
        );

        try {
            chatStreamExecutor.execute(() ->
                    streamWithRetry(requestBody, isCancelled, onChunk, onComplete, onError, retryCountOut));
        } catch (Exception e) {
            // 线程池队列满 / 拒绝
            log.warn("OpenAI 流式线程池拒绝任务", e);
            onError.accept(new AIServiceException("overloaded", "AI 服务繁忙，请稍后重试", e));
        }
    }

    /**
     * 流式有限重试：
     *   - 只重试 timeout / rate_limit / upstream / network
     *   - 已发送过首 chunk 后不再重试（避免前端收到重复内容）
     *   - 用户取消后立即结束
     *   - 每次退避等待结束后、发起下一次请求前再次检查取消
     *   - 重试耗尽后保留最后一次失败的真实 errorType（不统一改成 upstream）
     *   - retryCountOut 在真正回调 onComplete/onError 前写入实际重试次数
     */
    private void streamWithRetry(Map<String, Object> requestBody, BooleanSupplier isCancelled,
                                 Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError,
                                 AtomicInteger retryCountOut) {
        int maxAttempts = props.getMaxRetries() + 1;
        AtomicBoolean anyChunkSent = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        AIServiceException lastException = null;

        Consumer<String> guardedChunk = c -> {
            anyChunkSent.set(true);
            onChunk.accept(c);
        };
        Runnable guardedComplete = () -> {
            if (completed.compareAndSet(false, true)) {
                onComplete.run();
            }
        };

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (isCancelled.getAsBoolean()) return;
            // 本次尝试若成功/失败，实际重试次数即 attempt；必须早于 onComplete/onError 触发写回
            retryCountOut.set(attempt);
            try {
                if (attempt > 0) {
                    int delay = RETRY_DELAYS_MS[Math.min(attempt - 1, RETRY_DELAYS_MS.length - 1)];
                    log.info("AI 流式重试 {}/{}，等待 {}ms", attempt, props.getMaxRetries(), delay);
                    waitForRetryDelay(delay);
                    // 退避等待期间可能已被取消：不再发起新的 HTTP 请求
                    if (isCancelled.getAsBoolean()) {
                        log.info("AI 流式重试前已被取消，停止");
                        return;
                    }
                }
                doStreamCall(requestBody, isCancelled, guardedChunk, guardedComplete);
                return; // 流正常完成
            } catch (AIServiceException e) {
                lastException = e;
                if (isCancelled.getAsBoolean()) return;
                if (!isRetryable(e.getErrorType()) || anyChunkSent.get()) {
                    log.warn("AI 流式失败(不重试): errorType={}, attempt={}", e.getErrorType(), attempt + 1);
                    onError.accept(e);
                    return;
                }
                log.warn("AI 流式失败(可重试): errorType={}, attempt={}/{}",
                        e.getErrorType(), attempt + 1, maxAttempts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!isCancelled.getAsBoolean()) {
                    onError.accept(new AIServiceException("cancelled", "流式任务被中断", e));
                }
                return;
            } catch (Exception e) {
                log.error("AI 流式调用异常", e);
                onError.accept(e);
                return;
            }
        }
        // 重试耗尽：保留最后一次失败的真实 errorType
        retryCountOut.set(props.getMaxRetries());
        String finalMessage = "AI 流式调用失败，已重试 " + props.getMaxRetries() + " 次"
                + (lastException != null ? ": " + lastException.getMessage() : "");
        onError.accept(new AIServiceException(
                lastException != null ? lastException.getErrorType() : "upstream",
                finalMessage,
                lastException != null ? lastException.getCause() : null));
    }

    /** 重试等待间隔（可被子类覆盖以加速测试） */
    protected void waitForRetryDelay(long delayMs) throws InterruptedException {
        Thread.sleep(delayMs);
    }

    /**
     * 单次流式 HTTP 调用（同步阻塞，在流式线程池线程中执行）
     * <p>
     * 在 exchange 回调中检查非 2xx 状态码并分类；随后逐行解析 SSE。
     */
    void doStreamCall(Map<String, Object> requestBody, BooleanSupplier isCancelled,
                      Consumer<String> onChunk, Runnable onComplete) throws IOException {
        try {
            restClient.post()
                    .uri(props.getOpenai().getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + props.getOpenai().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange((req, resp) -> {
                        HttpStatusCode status = resp.getStatusCode();
                        if (status.is4xxClientError() || status.is5xxServerError()) {
                            int code = status.value();
                            String errorType = mapHttpError(code, "");
                            log.warn("AI 流式 HTTP 错误: code={}, errorType={}", code, errorType);
                            throw new AIServiceException(errorType, "模型服务返回 " + code);
                        }

                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (isCancelled.getAsBoolean()) {
                                    log.info("AI 流式被取消，停止读取");
                                    return null;
                                }
                                if (line.startsWith("data: [DONE]")) {
                                    break; // 流结束标记，显式跳出
                                }
                                if (line.startsWith("data: ")) {
                                    String json = line.substring(6);
                                    try {
                                        JsonNode node = objectMapper.readTree(json);
                                        JsonNode choices = node.path("choices");
                                        if (choices.isArray() && choices.size() > 0) {
                                            JsonNode delta = choices.get(0).path("delta");
                                            if (delta.has("content")) {
                                                onChunk.accept(delta.path("content").asText());
                                            }
                                        }
                                    } catch (Exception ignored) { /* 跳过畸形行 */ }
                                }
                            }
                        }
                        if (!isCancelled.getAsBoolean()) {
                            onComplete.run();
                        }
                        return null;
                    });
        } catch (ResourceAccessException e) {
            // 连接失败 / 连接超时 / 读取超时 / 读取流中断 —— RestClient 统一包成 ResourceAccessException
            throw toStreamNetworkError(e);
        }
    }

    /**
     * 网络层异常 → 统一 AIServiceException（timeout / network）。
     * 与同步路径分类一致，保证进入 streamWithRetry 的可重试分支。
     */
    static AIServiceException toStreamNetworkError(ResourceAccessException e) {
        if (e.getCause() instanceof SocketTimeoutException) {
            return new AIServiceException("timeout", "AI 流式调用超时", e);
        }
        if (e.getCause() instanceof ConnectException) {
            return new AIServiceException("network", "无法连接到 AI 服务", e);
        }
        return new AIServiceException("network", "AI 流式网络错误", e);
    }
}
