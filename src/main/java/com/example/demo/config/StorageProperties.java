package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * 文件存储配置 —— 绑定 application.yml 中 storage.* 下的所有配置
 * <p>
 * 第一版本地存储（storage.type=local），后续可切换 MinIO 实现而无需改业务代码。
 */
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /** 存储实现：local（后续可扩展 minio） */
    private String type = "local";

    /** 业务层允许的单文件最大大小（需与 spring.servlet.multipart 上限保持一致） */
    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    private Local local = new Local();

    private Minio minio = new Minio();

    public static class Local {
        /** 本地存储根目录（相对路径基于应用工作目录） */
        private String rootPath = "./data/uploads";

        public String getRootPath() { return rootPath; }
        public void setRootPath(String rootPath) { this.rootPath = rootPath; }
    }

    public static class Minio {
        /** MinIO 服务地址（含 http/https） */
        private String endpoint = "http://localhost:9000";
        /** Access Key —— 从环境变量读取，禁止硬编码 */
        private String accessKey = "";
        /** Secret Key —— 从环境变量读取，禁止硬编码 */
        private String secretKey = "";
        /** 目标 bucket */
        private String bucket = "ai-app-files";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public DataSize getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(DataSize maxFileSize) { this.maxFileSize = maxFileSize; }
    public Local getLocal() { return local; }
    public void setLocal(Local local) { this.local = local; }
    public Minio getMinio() { return minio; }
    public void setMinio(Minio minio) { this.minio = minio; }
}
