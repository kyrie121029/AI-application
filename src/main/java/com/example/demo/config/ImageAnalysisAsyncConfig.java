package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 图片分析专用线程池 —— 与文档解析（fileParseExecutor）隔离：图片分析是外部模型网络 IO。
 * <p>
 * @EnableAsync 由 FileParseAsyncConfig 统一启用，这里只定义 executor bean。
 */
@Configuration
public class ImageAnalysisAsyncConfig {

    @Bean(name = "imageAnalysisExecutor")
    public ThreadPoolTaskExecutor imageAnalysisExecutor(
            @Value("${image-analysis.core-pool-size:2}") int corePoolSize,
            @Value("${image-analysis.max-pool-size:4}") int maxPoolSize,
            @Value("${image-analysis.queue-capacity:50}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("image-analysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
