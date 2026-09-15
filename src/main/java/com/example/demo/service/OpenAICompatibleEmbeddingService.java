package com.example.demo.service;

import com.example.demo.config.AIProperties;
import com.example.demo.config.EmbeddingProperties;
import com.example.demo.dto.BatchEmbeddingResult;
import com.example.demo.dto.EmbeddingResult;
import com.example.demo.exception.AIServiceException;
import com.example.demo.exception.EmbeddingCallException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Embedding 实现 —— POST {baseUrl}/embeddings。
 * <p>
 * 严格校验响应：数量一致、index 无缺失/重复/越界、向量非空、维度一致；
 * 按 index 恢复输入顺序（不依赖 JSON 数组天然顺序）。复用 OpenAIAIService 的 errorType 分类。
 */
@Service
@ConditionalOnProperty(name = "embedding.provider", havingValue = "openai")
public class OpenAICompatibleEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleEmbeddingService.class);
    private static final int[] RETRY_DELAYS_MS = {1000, 2000, 4000};

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AIProperties aiProperties;
    private final EmbeddingProperties embeddingProperties;

    public OpenAICompatibleEmbeddingService(AIProperties aiProperties,
                                            EmbeddingProperties embeddingProperties) {
        this.aiProperties = aiProperties;
        this.embeddingProperties = embeddingProperties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiProperties.getConnectTimeout());
        factory.setReadTimeout(aiProperties.getReadTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public EmbeddingResult embed(String text) {
        if (text == null || text.isBlank()) {
            throw new AIServiceException("bad_request", "Embedding 输入为空");
        }
        BatchEmbeddingResult batch = embedBatch(List.of(text));
        if (batch.embeddings().size() != 1) {
            throw new AIServiceException("invalid_response", "单文本 Embedding 应返回 1 条向量");
        }
        BatchEmbeddingResult.EmbeddingVector v = batch.embeddings().get(0);
        return new EmbeddingResult(batch.provider(), batch.model(), batch.dimension(),
                v.vector(), batch.inputTokens(), batch.retryCount());
    }

    @Override
    public BatchEmbeddingResult embedBatch(List<String> texts) {
        validateInput(texts);
        int maxAttempts = embeddingProperties.getMaxRetries() + 1;
        AIServiceException lastException = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                if (attempt > 0) {
                    int delay = RETRY_DELAYS_MS[Math.min(attempt - 1, RETRY_DELAYS_MS.length - 1)];
                    log.info("Embedding 重试 {}/{}，等待 {}ms", attempt, embeddingProperties.getMaxRetries(), delay);
                    Thread.sleep(delay);
                }
                String responseBody = callEmbeddings(texts);
                return parseResponse(responseBody, texts.size(), attempt);
            } catch (AIServiceException e) {
                lastException = e;
                if (!OpenAIAIService.isRetryable(e.getErrorType())) {
                    // 不可重试：携带当前已重试次数（attempt）抛 EmbeddingCallException
                    throw new EmbeddingCallException(e.getErrorType(), e.getMessage(), attempt);
                }
                log.warn("Embedding 失败(可重试): errorType={}, attempt={}/{}",
                        e.getErrorType(), attempt + 1, maxAttempts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EmbeddingCallException("network", "Embedding 重试被中断", e, attempt);
            }
        }
        throw new EmbeddingCallException(lastException != null ? lastException.getErrorType() : "upstream",
                "Embedding 调用失败，已重试 " + embeddingProperties.getMaxRetries() + " 次",
                embeddingProperties.getMaxRetries());
    }

    /** 真实 HTTP 调用（package-private，测试子类可 override） */
    String callEmbeddings(List<String> texts) {
        try {
            return restClient.post()
                    .uri(aiProperties.getOpenai().getBaseUrl() + "/embeddings")
                    .header("Authorization", "Bearer " + aiProperties.getOpenai().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", getModelName(), "input", texts))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        throw new AIServiceException(OpenAIAIService.mapHttpError(code, ""), "Embedding 服务返回 " + code);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        throw new AIServiceException(OpenAIAIService.mapHttpError(code, ""), "Embedding 服务返回 " + code);
                    })
                    .body(String.class);
        } catch (AIServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new AIServiceException("timeout", "Embedding 超时", e);
            }
            if (e.getCause() instanceof ConnectException) {
                throw new AIServiceException("network", "无法连接 Embedding 服务", e);
            }
            throw new AIServiceException("network", "Embedding 网络错误", e);
        }
    }

    /** 解析 + 严格校验 index / dimension，并按 index 恢复输入顺序（package-private，测试可调用） */
    BatchEmbeddingResult parseResponse(String responseBody, int expectedCount, int retryCount) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new AIServiceException("invalid_response", "Embedding 响应不是合法 JSON", e);
        }
        JsonNode data = root.path("data");
        int dataSize = data.isArray() ? data.size() : 0;
        if (!data.isArray() || dataSize != expectedCount) {
            throw new AIServiceException("invalid_response",
                    "Embedding 返回数量(" + dataSize + ")与输入(" + expectedCount + ")不一致");
        }

        int dim = getDimension();
        float[][] vectors = new float[expectedCount][];
        boolean[] seen = new boolean[expectedCount];
        for (JsonNode item : data) {
            JsonNode indexNode = item.path("index");
            if (indexNode.isMissingNode() || !indexNode.isInt()) {
                throw new AIServiceException("invalid_response", "Embedding 响应缺少 index");
            }
            int index = indexNode.asInt();
            if (index < 0 || index >= expectedCount) {
                throw new AIServiceException("invalid_response", "Embedding index 越界: " + index);
            }
            if (seen[index]) {
                throw new AIServiceException("invalid_response", "Embedding index 重复: " + index);
            }
            seen[index] = true;

            JsonNode embedding = item.path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new AIServiceException("invalid_response", "Embedding 向量为空: index=" + index);
            }
            if (embedding.size() != dim) {
                throw new AIServiceException("dimension_mismatch",
                        "Embedding 维度(" + embedding.size() + ")与配置(" + dim + ")不一致");
            }
            float[] vec = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                JsonNode element = embedding.get(i);
                if (!element.isNumber()) {
                    throw new AIServiceException("invalid_response",
                            "Embedding 向量包含非数值元素: index=" + index);
                }
                float value = (float) element.asDouble();
                if (!Float.isFinite(value)) {
                    throw new AIServiceException("invalid_response",
                            "Embedding 向量包含非有限数值: index=" + index);
                }
                vec[i] = value;
            }
            vectors[index] = vec;
        }

        for (int i = 0; i < expectedCount; i++) {
            if (!seen[i]) {
                throw new AIServiceException("invalid_response", "Embedding 缺少 index=" + i);
            }
        }

        List<BatchEmbeddingResult.EmbeddingVector> result = new ArrayList<>(expectedCount);
        for (int i = 0; i < expectedCount; i++) {
            result.add(new BatchEmbeddingResult.EmbeddingVector(i, vectors[i]));
        }
        JsonNode usage = root.path("usage");
        Integer inputTokens = usage.path("prompt_tokens").isMissingNode()
                ? null : usage.path("prompt_tokens").asInt();
        return new BatchEmbeddingResult("openai", getModelName(), dim, result, inputTokens, retryCount);
    }

    private void validateInput(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new AIServiceException("bad_request", "Embedding 输入为空");
        }
        for (String t : texts) {
            if (t == null || t.isBlank()) {
                throw new AIServiceException("bad_request", "Embedding 输入包含空文本");
            }
        }
    }

    @Override
    public String getProvider() { return "openai"; }

    @Override
    public String getModelName() { return embeddingProperties.getModel(); }

    @Override
    public int getDimension() { return embeddingProperties.getDimension(); }
}
