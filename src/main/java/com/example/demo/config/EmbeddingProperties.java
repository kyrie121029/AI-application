package com.example.demo.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Embedding 配置 —— 绑定 application.yml 中 embedding.* 下的所有配置。
 * <p>
 * 连接级 base-url / api-key / timeout 复用 AIProperties；chat 与 embedding 的 model 分离。
 * <p>
 * dimension 语义：是「期望的响应维度」（expectedDimension），用于校验 Provider 返回的向量维度。
 * 请求体不发送 dimensions 参数，采用 Provider 默认输出维度；因此默认值必须与
 * text-embedding-3-small 的默认输出维度（1536）一致，否则校验会误报 dimension_mismatch。
 */
@Component
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    /** Embedding Provider：mock / openai（独立于 ai.provider） */
    private String provider = "mock";

    /** Embedding 模型名（独立于 chat model） */
    private String model = "text-embedding-3-small";

    /** 期望响应维度（expectedDimension），校验 Provider 返回的向量维度 */
    private int dimension = 1536;

    /** 应用层批量大小（非 Provider API 上限） */
    private int batchSize = 32;

    /** 有限重试次数（仅对可重试异常） */
    private int maxRetries = 3;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    /** 启动时校验非法配置快速失败 */
    @PostConstruct
    public void validate() {
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("embedding.model 不能为空");
        }
        if (dimension <= 0) {
            throw new IllegalStateException("embedding.dimension 必须大于 0");
        }
        if (batchSize <= 0) {
            throw new IllegalStateException("embedding.batch-size 必须大于 0");
        }
        if (maxRetries < 0) {
            throw new IllegalStateException("embedding.max-retries 必须 >= 0");
        }
    }
}
