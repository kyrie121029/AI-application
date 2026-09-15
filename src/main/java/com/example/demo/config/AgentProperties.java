package com.example.demo.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Agent 循环边界配置 —— agent.*。
 */
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** 最大工具执行轮数（含第 1 轮） */
    private int maxRounds = 5;

    /** Agent 总超时 */
    private Duration timeout = Duration.ofSeconds(60);

    public int getMaxRounds() { return maxRounds; }
    public void setMaxRounds(int maxRounds) { this.maxRounds = maxRounds; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }

    @PostConstruct
    public void validate() {
        if (maxRounds <= 0) {
            throw new IllegalStateException("agent.max-rounds 必须大于 0");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("agent.timeout 必须为正");
        }
    }
}
