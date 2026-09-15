package com.example.demo.service;

import com.example.demo.config.AIProperties;
import com.example.demo.config.ImageAnalysisProperties;
import com.example.demo.dto.ImageAnalysisResult;
import com.example.demo.exception.MultimodalAIException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容多模态实现 —— 通过 messages content 数组传入 image_url（base64 data URL）。
 * <p>
 * 结构化输出：要求模型返回 JSON，ObjectMapper 解析为 ImageAnalysisResult；
 * 非法 JSON 有限重试，不无限重试。Base64 仅在本次请求内存中，不落库不打日志。
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAIMultimodalAIService implements MultimodalAIService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIMultimodalAIService.class);
    private static final int MAX_JSON_RETRY = 2;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AIProperties aiProperties;
    private final ImageAnalysisProperties imageAnalysisProperties;

    public OpenAIMultimodalAIService(AIProperties aiProperties,
                                     ImageAnalysisProperties imageAnalysisProperties) {
        this.aiProperties = aiProperties;
        this.imageAnalysisProperties = imageAnalysisProperties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiProperties.getConnectTimeout());
        factory.setReadTimeout(aiProperties.getReadTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public ImageAnalysisResult analyzeImage(byte[] imageBytes, String mimeType, String prompt) {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:" + mimeType + ";base64," + base64;

        Map<String, Object> body = Map.of(
                "model", model(),
                "max_tokens", imageAnalysisProperties.getMaxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", prompt),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", "请分析这张图片"),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                        ))
                ));

        for (int attempt = 0; attempt <= MAX_JSON_RETRY; attempt++) {
            String responseBody = call(body);
            try {
                return parse(responseBody);
            } catch (MultimodalAIException e) {
                if (attempt == MAX_JSON_RETRY) throw e;
                log.warn("多模态结构化输出非法，重试 {}/{}", attempt + 1, MAX_JSON_RETRY);
            }
        }
        throw new MultimodalAIException("多模态分析失败（结构化输出非法）");
    }

    private String call(Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri(aiProperties.getOpenai().getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + aiProperties.getOpenai().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new MultimodalAIException("多模态模型调用失败: " + e.getMessage(), e);
        }
    }

    private ImageAnalysisResult parse(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new MultimodalAIException("多模态响应不是合法 JSON", e);
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new MultimodalAIException("多模态响应缺少 choices");
        }
        String content = choices.get(0).path("message").path("content").asText();
        JsonNode json;
        try {
            String cleaned = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            json = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            throw new MultimodalAIException("多模态输出不是合法 JSON", e);
        }
        return new ImageAnalysisResult(
                json.path("summary").asText(),
                asStringList(json.path("objects")),
                json.path("scene").asText(),
                json.path("textContent").asText(),
                asStringList(json.path("riskFlags")));
    }

    private List<String> asStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(n -> list.add(n.asText()));
        }
        return list;
    }

    private String model() {
        String m = imageAnalysisProperties.getModel();
        return (m == null || m.isBlank()) ? aiProperties.getOpenai().getModel() : m;
    }

    @Override
    public String getProvider() { return "openai"; }

    @Override
    public String getModelName() { return model(); }
}
