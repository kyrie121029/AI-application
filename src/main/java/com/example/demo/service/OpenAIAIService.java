package com.example.demo.service;

import com.example.demo.config.AIProperties;
import com.example.demo.config.PromptTemplate;
import com.example.demo.dto.AIResult;
import com.example.demo.exception.AIResponseParseException;
import com.example.demo.exception.AIServiceException;
import com.example.demo.model.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容 API 服务 —— 调用真实大模型
 * <p>
 * 改进：
 *   1. AIProperties 统一配置（替代 @Value）
 *   2. PromptTemplate 变量替换（替代 + 拼接）
 *   3. 有限重试 + 指数退避
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAIAIService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIAIService.class);
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AIProperties props;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;

    private static final int[] RETRY_DELAYS_MS = {1000, 2000, 4000}; // 1s, 2s, 4s

    public OpenAIAIService(AIProperties props) {
        this.props = props;
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

    @Override
    public AIResult analyze(String inputText, String taskType) {
        // 构建 Prompt（模板 + 变量替换）
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

        // 带重试的 HTTP 调用
        long startTime = System.currentTimeMillis();
        String responseBody = callWithRetry(requestBody);
        long elapsed = System.currentTimeMillis() - startTime;

        AIResult result = parseResponse(responseBody);
        log.info("OpenAI 完成: model={}, inputTokens={}, outputTokens={}, elapsed={}ms",
                result.getModelName(), result.getInputTokens(), result.getOutputTokens(), elapsed);
        return result;
    }

    /**
     * 有限重试：仅对可恢复异常（timeout/rate_limit/server/network）重试
     * 不可重试：auth（401）、parse error、client_error
     */
    private String callWithRetry(Map<String, Object> requestBody) {
        int maxAttempts = props.getMaxRetries() + 1; // 1 次原始 + N 次重试
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
                    throw e; // 不可重试，直接抛出
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

    /** 发送 HTTP 请求（单次） */
    private String doCall(Map<String, Object> requestBody) {
        try {
            return restClient.post()
                    .uri(props.getOpenai().getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + props.getOpenai().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        byte[] body = resp.getBody().readAllBytes();
                        String bodyStr = new String(body);
                        int code = resp.getStatusCode().value();
                        if (code == 401)
                            throw new AIServiceException("auth", "API Key 无效");
                        if (code == 429)
                            throw new AIServiceException("rate_limit", "请求频率超限");
                        throw new AIServiceException("client_error", "客户端错误 " + code);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new AIServiceException("server",
                                "AI 服务端错误 " + resp.getStatusCode().value());
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
            throw new AIServiceException("server", "HTTP " + e.getStatusCode().value(), e);
        }
    }

    /** 判断该错误类型能否重试 */
    static boolean isRetryable(String errorType) {
        return "timeout".equals(errorType) || "rate_limit".equals(errorType)
                || "server".equals(errorType) || "network".equals(errorType);
    }

    // ========== 解析逻辑（不变） ==========

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

    /** 供 TaskService 记录 promptVersion */
    public String getPromptVersion() { return systemPromptTemplate.getVersion(); }

    // ========== Phase 5: 流式对话 ==========

    @Override
    public void streamAnalyze(String systemPrompt, List<Message> history,
                              Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
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

        new Thread(() -> {
            try {
                restClient.post()
                        .uri(props.getOpenai().getBaseUrl() + "/chat/completions")
                        .header("Authorization", "Bearer " + props.getOpenai().getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .exchange((req, resp) -> {
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.startsWith("data: ") && !line.equals("data: [DONE]")) {
                                        String json = line.substring(6);
                                        try {
                                            JsonNode node = objectMapper.readTree(json);
                                            JsonNode delta = node.path("choices").get(0).path("delta");
                                            if (delta.has("content")) {
                                                onChunk.accept(delta.path("content").asText());
                                            }
                                        } catch (Exception ignored) { /* skip malformed lines */ }
                                    }
                                }
                            }
                            onComplete.run();
                            return null;
                        });
            } catch (Exception e) {
                log.error("OpenAI 流式调用失败", e);
                onError.accept(e);
            }
        }).start();
    }
}
