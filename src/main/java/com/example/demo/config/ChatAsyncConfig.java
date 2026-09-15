package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 聊天流式专用线程池配置
 * <p>
 * 替代直接 new Thread：
 *   - 有界队列 + 有界最大线程数，防止每个请求无限创建平台线程
 *   - 队列满时抛 RejectedExecutionException，由调用方转成 overloaded 错误
 */
@Configuration
public class ChatAsyncConfig {

    @Bean(name = "chatStreamExecutor")
    public ThreadPoolTaskExecutor chatStreamExecutor(
            @Value("${chat.executor.core-pool-size:8}") int corePoolSize,
            @Value("${chat.executor.max-pool-size:32}") int maxPoolSize,
            @Value("${chat.executor.queue-capacity:20}") int queueCapacity,
            @Value("${chat.executor.keep-alive-seconds:60}") int keepAliveSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("chat-stream-");
        // 队列满 + 线程满 → 直接拒绝，让调用方感知过载
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
