package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 文档解析专用线程池 —— 解析任务脱离 HTTP 请求线程。
 * <p>
 * 不直接使用默认 SimpleAsyncTaskExecutor / 公共默认线程池；有界队列 + AbortPolicy 防过载。
 */
@Configuration
@EnableAsync
public class FileParseAsyncConfig {

    @Bean(name = "fileParseExecutor")
    public ThreadPoolTaskExecutor fileParseExecutor(
            @Value("${file-parse.core-pool-size:2}") int corePoolSize,
            @Value("${file-parse.max-pool-size:4}") int maxPoolSize,
            @Value("${file-parse.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("file-parse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
