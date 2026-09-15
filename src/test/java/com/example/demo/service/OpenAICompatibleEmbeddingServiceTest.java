package com.example.demo.service;

import com.example.demo.config.AIProperties;
import com.example.demo.config.EmbeddingProperties;
import com.example.demo.dto.BatchEmbeddingResult;
import com.example.demo.dto.EmbeddingResult;
import com.example.demo.exception.AIServiceException;
import com.example.demo.exception.EmbeddingCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAICompatibleEmbeddingService 单元测试 —— parseResponse 的 index 校验 / 维度校验 / 空输入 / 重试。
 * 不依赖真实 HTTP（parseResponse 直接调用；retry 用匿名子类 override callEmbeddings）。
 */
class OpenAICompatibleEmbeddingServiceTest {

    private AIProperties aiProperties;
    private EmbeddingProperties embeddingProperties;

    @BeforeEach
    void setUp() {
        aiProperties = new AIProperties();
        embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setModel("text-embedding-3-small");
        embeddingProperties.setDimension(4);
        embeddingProperties.setBatchSize(32);
        embeddingProperties.setMaxRetries(2);
    }

    private OpenAICompatibleEmbeddingService newService() {
        return new OpenAICompatibleEmbeddingService(aiProperties, embeddingProperties);
    }

    private String item(int index, double... values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(values[i]);
        }
        sb.append("]");
        return "{\"index\":" + index + ",\"embedding\":" + sb + "}";
    }

    private String body(String... items) {
        return "{\"data\":[" + String.join(",", items) + "],\"usage\":{\"prompt_tokens\":10}}";
    }

    @Test
    @DisplayName("正常响应：按 index 顺序返回")
    void normalResponse() {
        String json = body(item(0, 1, 1, 1, 1), item(1, 2, 2, 2, 2), item(2, 3, 3, 3, 3));
        BatchEmbeddingResult r = newService().parseResponse(json, 3, 0);
        assertEquals(3, r.embeddings().size());
        assertEquals(0, r.embeddings().get(0).index());
        assertEquals(2, r.embeddings().get(2).index());
        assertEquals(Integer.valueOf(10), r.inputTokens());
    }

    @Test
    @DisplayName("乱序 index（2/0/1）→ 恢复正确顺序")
    void outOfOrderIndexRecovered() {
        String json = body(item(2, 3, 3, 3, 3), item(0, 1, 1, 1, 1), item(1, 2, 2, 2, 2));
        BatchEmbeddingResult r = newService().parseResponse(json, 3, 0);
        // 恢复后：get(0) 对应输入 0，get(1) 对应输入 1，get(2) 对应输入 2
        assertEquals(0, r.embeddings().get(0).index());
        assertEquals(1, r.embeddings().get(1).index());
        assertEquals(2, r.embeddings().get(2).index());
        assertEquals(1.0f, r.embeddings().get(0).vector()[0]); // 输入 0 的向量首元素 1
        assertEquals(3.0f, r.embeddings().get(2).vector()[0]); // 输入 2 的向量首元素 3
    }

    @Test
    @DisplayName("缺少 index → invalid_response")
    void missingIndexFails() {
        String json = body(item(0, 1, 1, 1, 1), item(2, 3, 3, 3, 3));
        AIServiceException e = assertThrows(AIServiceException.class,
                () -> newService().parseResponse(json, 3, 0));
        assertEquals("invalid_response", e.getErrorType());
    }

    @Test
    @DisplayName("重复 index → invalid_response")
    void duplicateIndexFails() {
        String json = body(item(0, 1, 1, 1, 1), item(1, 2, 2, 2, 2), item(1, 3, 3, 3, 3));
        AIServiceException e = assertThrows(AIServiceException.class,
                () -> newService().parseResponse(json, 3, 0));
        assertEquals("invalid_response", e.getErrorType());
    }

    @Test
    @DisplayName("index 越界 → invalid_response")
    void outOfRangeIndexFails() {
        String json = body(item(0, 1, 1, 1, 1), item(1, 2, 2, 2, 2), item(5, 3, 3, 3, 3));
        AIServiceException e = assertThrows(AIServiceException.class,
                () -> newService().parseResponse(json, 3, 0));
        assertEquals("invalid_response", e.getErrorType());
    }

    @Test
    @DisplayName("返回数量错误 → invalid_response")
    void wrongCountFails() {
        String json = body(item(0, 1, 1, 1, 1), item(1, 2, 2, 2, 2));
        AIServiceException e = assertThrows(AIServiceException.class,
                () -> newService().parseResponse(json, 3, 0));
        assertEquals("invalid_response", e.getErrorType());
    }

    @Test
    @DisplayName("维度与配置不一致 → dimension_mismatch")
    void dimensionMismatchFails() {
        String json = body(item(0, 1, 1, 1)); // 3 维，配置 4 维
        AIServiceException e = assertThrows(AIServiceException.class,
                () -> newService().parseResponse(json, 1, 0));
        assertEquals("dimension_mismatch", e.getErrorType());
    }

    @Test
    @DisplayName("同一批内维度不一致 → dimension_mismatch")
    void inconsistentDimensionInBatchFails() {
        String json = body(item(0, 1, 1, 1, 1), item(1, 2, 2, 2)); // 第二个 3 维
        AIServiceException e = assertThrows(AIServiceException.class,
                () -> newService().parseResponse(json, 2, 0));
        assertEquals("dimension_mismatch", e.getErrorType());
    }

    @Test
    @DisplayName("向量为空 → invalid_response")
    void emptyVectorFails() {
        String json = "{\"data\":[{\"index\":0,\"embedding\":[]}]}";
        AIServiceException e = assertThrows(AIServiceException.class,
                () -> newService().parseResponse(json, 1, 0));
        assertEquals("invalid_response", e.getErrorType());
    }

    @Test
    @DisplayName("空输入拒绝，不发 HTTP")
    void rejectsEmptyInput() {
        OpenAICompatibleEmbeddingService svc = newService();
        assertThrows(AIServiceException.class, () -> svc.embed(null));
        assertThrows(AIServiceException.class, () -> svc.embed("  "));
        assertThrows(AIServiceException.class, () -> svc.embedBatch(List.of()));
        assertThrows(AIServiceException.class, () -> svc.embedBatch(java.util.Arrays.asList("a", null)));
    }

    @Test
    @DisplayName("rate_limit 有限重试后成功，retryCount 正确")
    void retriesRateLimit() {
        OpenAICompatibleEmbeddingService svc = new OpenAICompatibleEmbeddingService(aiProperties, embeddingProperties) {
            int call = 0;

            @Override
            String callEmbeddings(List<String> texts) {
                call++;
                if (call == 1) throw new AIServiceException("rate_limit", "限流");
                return body(item(0, 1, 1, 1, 1));
            }
        };

        BatchEmbeddingResult r = svc.embedBatch(List.of("a"));
        assertEquals(1, r.retryCount());
    }

    @Test
    @DisplayName("重试耗尽后保留真实 errorType + retryCount=maxRetries")
    void retryExhaustionKeepsErrorType() {
        OpenAICompatibleEmbeddingService svc = new OpenAICompatibleEmbeddingService(aiProperties, embeddingProperties) {
            @Override
            String callEmbeddings(List<String> texts) {
                throw new AIServiceException("rate_limit", "限流");
            }
        };

        EmbeddingCallException e = assertThrows(EmbeddingCallException.class,
                () -> svc.embedBatch(List.of("a")));
        assertEquals("rate_limit", e.getErrorType());
        assertEquals(2, e.getRetryCount()); // maxRetries=2
    }

    @Test
    @DisplayName("auth 错误不重试，retryCount=0")
    void authNotRetried() {
        int[] callCount = {0};
        OpenAICompatibleEmbeddingService svc = new OpenAICompatibleEmbeddingService(aiProperties, embeddingProperties) {
            @Override
            String callEmbeddings(List<String> texts) {
                callCount[0]++;
                throw new AIServiceException("auth", "认证失败");
            }
        };

        EmbeddingCallException e = assertThrows(EmbeddingCallException.class,
                () -> svc.embedBatch(List.of("a")));
        assertEquals("auth", e.getErrorType());
        assertEquals(0, e.getRetryCount());
        assertEquals(1, callCount[0]);
    }

    @Test
    @DisplayName("Single Embedding 保留 retryCount")
    void singlePreservesRetryCount() {
        OpenAICompatibleEmbeddingService svc = new OpenAICompatibleEmbeddingService(aiProperties, embeddingProperties) {
            int call = 0;

            @Override
            String callEmbeddings(List<String> texts) {
                call++;
                if (call == 1) throw new AIServiceException("rate_limit", "限流");
                return body(item(0, 1, 1, 1, 1));
            }
        };

        EmbeddingResult r = svc.embed("a");
        assertEquals(1, r.retryCount());
    }

    @Test
    @DisplayName("向量元素非 number → invalid_response")
    void nonNumberElementFails() {
        String json = "{\"data\":[{\"index\":0,\"embedding\":[1.0,\"abc\",1.0,1.0]}]}";
        AIServiceException e = assertThrows(AIServiceException.class,
                () -> newService().parseResponse(json, 1, 0));
        assertEquals("invalid_response", e.getErrorType());
    }

    @Test
    @DisplayName("向量元素非有限数值 → invalid_response")
    void nonFiniteElementFails() {
        // 1e999 溢出为 Infinity，Float.isFinite 为 false
        String json = "{\"data\":[{\"index\":0,\"embedding\":[1e999,0,0,0]}]}";
        AIServiceException e = assertThrows(AIServiceException.class,
                () -> newService().parseResponse(json, 1, 0));
        assertEquals("invalid_response", e.getErrorType());
    }
}
