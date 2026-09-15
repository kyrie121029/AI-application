package com.example.demo.service;

import com.example.demo.config.AIProperties;
import com.example.demo.config.EmbeddingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Embedding Provider 条件装配测试 —— embedding.provider 独立于 ai.provider。
 */
class EmbeddingConditionTest {

    private ApplicationContextRunner runner(String provider) {
        return new ApplicationContextRunner()
                .withPropertyValues("embedding.provider=" + provider)
                .withBean(EmbeddingProperties.class, EmbeddingProperties::new)
                .withBean(AIProperties.class, AIProperties::new)
                .withUserConfiguration(MockEmbeddingService.class, OpenAICompatibleEmbeddingService.class);
    }

    @Test
    @DisplayName("embedding.provider=mock → 装配 MockEmbeddingService")
    void mockLoadsMockEmbedding() {
        runner("mock").run(ctx -> {
            assertTrue(ctx.containsBean("mockEmbeddingService"));
            assertFalse(ctx.containsBean("openAICompatibleEmbeddingService"));
        });
    }

    @Test
    @DisplayName("embedding.provider=openai → 装配 OpenAICompatibleEmbeddingService")
    void openaiLoadsOpenAIEmbedding() {
        runner("openai").run(ctx -> {
            assertTrue(ctx.containsBean("openAICompatibleEmbeddingService"));
            assertFalse(ctx.containsBean("mockEmbeddingService"));
        });
    }
}
