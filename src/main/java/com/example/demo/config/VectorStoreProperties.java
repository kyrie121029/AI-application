package com.example.demo.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 向量存储配置 —— 绑定 application.yml 中 vector-store.* 下的所有配置。
 */
@Component
@ConfigurationProperties(prefix = "vector-store")
public class VectorStoreProperties {

    /** 向量存储实现：qdrant（未来可扩展其他） */
    private String type = "qdrant";

    private Qdrant qdrant = new Qdrant();

    public static class Qdrant {
        /** Qdrant 服务地址 */
        private String host = "localhost";
        /** Qdrant REST 端口 */
        private int port = 6333;
        /** 集合名 */
        private String collection = "ai-app-vectors";

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Qdrant getQdrant() { return qdrant; }
    public void setQdrant(Qdrant qdrant) { this.qdrant = qdrant; }

    /** 启动时结构性校验（Qdrant 可达性/维度一致性在首次 ensureCollection 时校验） */
    @PostConstruct
    public void validate() {
        if (qdrant.getPort() <= 0) {
            throw new IllegalStateException("vector-store.qdrant.port 必须大于 0");
        }
        if (qdrant.getHost() == null || qdrant.getHost().isBlank()) {
            throw new IllegalStateException("vector-store.qdrant.host 不能为空");
        }
        if (qdrant.getCollection() == null || qdrant.getCollection().isBlank()) {
            throw new IllegalStateException("vector-store.qdrant.collection 不能为空");
        }
    }
}