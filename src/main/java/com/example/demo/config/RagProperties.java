package com.example.demo.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 配置 —— 绑定 application.yml 中 rag.* 下的所有配置。
 */
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Chunk chunk = new Chunk();

    private Index index = new Index();

    private Retrieval retrieval = new Retrieval();

    private Context context = new Context();

    public static class Chunk {
        /** 单个 Chunk 最大字符数（基于字符，非 token） */
        private int maxChars = 1000;
        /** 相邻 Chunk 之间的重叠字符数 */
        private int overlapChars = 100;

        public int getMaxChars() { return maxChars; }
        public void setMaxChars(int maxChars) { this.maxChars = maxChars; }
        public int getOverlapChars() { return overlapChars; }
        public void setOverlapChars(int overlapChars) { this.overlapChars = overlapChars; }
    }

    /** 文档索引版本 —— 重建索引/清空派生索引时作为标识与区分 */
    public static class Index {
        private String version = "rag-index-v1";

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    /** 检索默认参数 */
    public static class Retrieval {
        /** 默认返回条数 */
        private int topK = 5;
        /** 相似度下限（>0 时启用；Cosine 得分，0 表示不设阈值） */
        private double scoreThreshold = 0.0;

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public double getScoreThreshold() { return scoreThreshold; }
        public void setScoreThreshold(double scoreThreshold) { this.scoreThreshold = scoreThreshold; }
    }

    /** Context Assembly 默认参数 */
    public static class Context {
        /** 送入模型的检索上下文最大 Token 数（估算） */
        private int maxContextTokens = 2000;

        public int getMaxContextTokens() { return maxContextTokens; }
        public void setMaxContextTokens(int maxContextTokens) { this.maxContextTokens = maxContextTokens; }
    }

    public Chunk getChunk() { return chunk; }
    public void setChunk(Chunk chunk) { this.chunk = chunk; }
    public Index getIndex() { return index; }
    public void setIndex(Index index) { this.index = index; }
    public Retrieval getRetrieval() { return retrieval; }
    public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }
    public Context getContext() { return context; }
    public void setContext(Context context) { this.context = context; }

    /** 启动时校验非法配置快速失败 */
    @PostConstruct
    public void validate() {
        if (chunk.getMaxChars() <= 0) {
            throw new IllegalStateException("rag.chunk.max-chars 必须大于 0");
        }
        if (chunk.getOverlapChars() < 0) {
            throw new IllegalStateException("rag.chunk.overlap-chars 必须 >= 0");
        }
        if (chunk.getOverlapChars() >= chunk.getMaxChars()) {
            throw new IllegalStateException("rag.chunk.overlap-chars 必须小于 max-chars");
        }
        if (retrieval.getTopK() <= 0) {
            throw new IllegalStateException("rag.retrieval.top-k 必须大于 0");
        }
        if (retrieval.getScoreThreshold() < 0 || retrieval.getScoreThreshold() > 1) {
            throw new IllegalStateException("rag.retrieval.score-threshold 必须在 [0, 1]");
        }
        if (context.getMaxContextTokens() <= 0) {
            throw new IllegalStateException("rag.context.max-context-tokens 必须大于 0");
        }
    }
}
