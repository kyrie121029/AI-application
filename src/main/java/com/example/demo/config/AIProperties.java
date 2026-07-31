package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * AI 统一配置 —— 替代分散的 @Value 注解
 * <p>
 * 绑定 application.yml 中 ai.* 下的所有配置。
 * 开发环境用 Mock、生产用 OpenAI，通过 Spring Profile 切换。
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AIProperties {

    /** AI 提供商：mock / openai */
    private String provider = "mock";

    /** OpenAI 兼容 API 配置 */
    private OpenAi openai = new OpenAi();

    /** 连接超时 */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /** 响应超时（AI 推理需要较长时间） */
    private Duration readTimeout = Duration.ofSeconds(60);

    /** 最大重试次数（仅对可重试异常生效） */
    private int maxRetries = 3;

    // ==================== 内部类 + Getter/Setter ====================

    public static class OpenAi {
        /** API Key —— 从环境变量 AI_OPENAI_API_KEY 或 DASHSCOPE_API_KEY 读取 */
        private String apiKey = "";

        /** API 地址（含版本路径，如 /v1），最终请求 = baseUrl + /chat/completions */
        private String baseUrl = "https://api.openai.com/v1";

        /** 模型名称 */
        private String model = "gpt-3.5-turbo";

        /** System Prompt 模板版本 */
        private String systemPromptVersion = "task-analysis-v1";

        /** System Prompt 模板内容 */
        private String systemPrompt = "你是一个专业的文本分析助手。请根据用户输入，生成以下三部分内容：\n1. 摘要：简要总结输入内容\n2. 结论：给出分析结论\n3. 建议：提供后续建议\n\n请用 JSON 格式输出，包含 summary、conclusion、suggestion 三个字段。";

        /** User Prompt 模板 */
        private String userPrompt = "任务类型：{taskType}\n待分析文本：{inputText}";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getSystemPromptVersion() { return systemPromptVersion; }
        public void setSystemPromptVersion(String v) { this.systemPromptVersion = v; }
        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String p) { this.systemPrompt = p; }
        public String getUserPrompt() { return userPrompt; }
        public void setUserPrompt(String p) { this.userPrompt = p; }
    }

    // ---- 顶层 Getter/Setter（@ConfigurationProperties 需要它们来绑定） ----

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public OpenAi getOpenai() { return openai; }
    public void setOpenai(OpenAi openai) { this.openai = openai; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
}
