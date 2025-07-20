package com.java.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Конфигурация для асинхронной обработки импорта файлов
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Value("${import.async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${import.async.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${import.async.queue-capacity:100}")
    private int queueCapacity;

    @Value("${import.async.thread-name-prefix:ImportExecutor-}")
    private String threadNamePrefix;

    /**
     * Основной пул потоков для импорта файлов
     */
    @Bean(name = "importTaskExecutor")
    public Executor importTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("Инициализирован пул потоков для импорта: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }

    /**
     * Пул потоков для анализа файлов (легковесные операции)
     */
    @Bean(name = "fileAnalysisExecutor")
    public Executor fileAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("FileAnalysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }
}