package com.java.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Конфигурация асинхронного экспорта файлов
 */
@Configuration
@Slf4j
public class ExportAsyncConfig {

    @Value("${export.async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${export.async.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${export.async.queue-capacity:100}")
    private int queueCapacity;

    @Value("${export.async.thread-name-prefix:ExportExecutor-}")
    private String threadNamePrefix;

    /**
     * Основной пул потоков для экспорта файлов
     */
    @Bean(name = "exportTaskExecutor")
    public Executor exportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("Инициализирован пул потоков для экспорта: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }
}
