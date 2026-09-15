package com.example.demo.service;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.dto.BatchEmbeddingResult;
import com.example.demo.dto.EmbeddingResult;
import com.example.demo.exception.AIServiceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Mock Embedding 实现 —— 开发/测试用，不调用真实模型。
 * <p>
 * 确定性：文本 hash → 伪向量，same input → same vector，不依赖随机数。
 */
@Service
@ConditionalOnProperty(name = "embedding.provider", havingValue = "mock", matchIfMissing = true)
public class MockEmbeddingService implements EmbeddingService {

    private final EmbeddingProperties properties;

    public MockEmbeddingService(EmbeddingProperties properties) {
        this.properties = properties;
    }

    @Override
    public EmbeddingResult embed(String text) {
        if (text == null || text.isBlank()) {
            throw new AIServiceException("bad_request", "Embedding 输入为空");
        }
        BatchEmbeddingResult batch = embedBatch(List.of(text));
        BatchEmbeddingResult.EmbeddingVector v = batch.embeddings().get(0);
        return new EmbeddingResult(batch.provider(), batch.model(), batch.dimension(),
                v.vector(), batch.inputTokens(), batch.retryCount());
    }

    @Override
    public BatchEmbeddingResult embedBatch(List<String> texts) {
        validateInput(texts);
        List<BatchEmbeddingResult.EmbeddingVector> vectors = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            vectors.add(new BatchEmbeddingResult.EmbeddingVector(i, pseudoVector(texts.get(i))));
        }
        return new BatchEmbeddingResult("mock", "mock-embedding", getDimension(), vectors, null, 0);
    }

    @Override
    public String getProvider() { return "mock"; }

    @Override
    public String getModelName() { return "mock-embedding"; }

    @Override
    public int getDimension() { return properties.getDimension(); }

    /** 确定性伪向量：FNV-1a 变体，same input → same vector */
    private float[] pseudoVector(String text) {
        int dim = getDimension();
        float[] v = new float[dim];
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < dim; i++) {
            int h = hash(bytes, i);
            v[i] = (h & 0xFFFF) / 65535.0f; // 归一化到 [0, 1]
        }
        return v;
    }

    private int hash(byte[] bytes, int seed) {
        int h = 0x811c9dc5 ^ seed;
        for (byte b : bytes) {
            h = (h * 16777619) ^ (b & 0xFF);
        }
        return h;
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
}
