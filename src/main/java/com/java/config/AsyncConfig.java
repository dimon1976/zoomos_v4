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
 * Конфигурация для асинхронной обработки импорта и экспорта файлов
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Value("${import.async.core-pool-size:2}")
    private int importCorePoolSize;

    @Value("${import.async.max-pool-size:4}")
    private int importMaxPoolSize;

    @Value("${import.async.queue-capacity:100}")
    private int importQueueCapacity;

    @Value("${import.async.thread-name-prefix:ImportExecutor-}")
    private String importThreadNamePrefix;

    @Value("${export.async.core-pool-size:2}")
    private int exportCorePoolSize;

    @Value("${export.async.max-pool-size:4}")
    private int exportMaxPoolSize;

    @Value("${export.async.queue-capacity:100}")
    private int exportQueueCapacity;

    @Value("${export.async.thread-name-prefix:ExportExecutor-}")
    private String exportThreadNamePrefix;

    /**
     * Основной пул потоков для импорта файлов
     */
    @Bean(name = "importTaskExecutor")
    public Executor importTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(importCorePoolSize);
        executor.setMaxPoolSize(importMaxPoolSize);
        executor.setQueueCapacity(importQueueCapacity);
        executor.setThreadNamePrefix(importThreadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("Инициализирован пул потоков для импорта: core={}, max={}, queue={}",
                importCorePoolSize, importMaxPoolSize, importQueueCapacity);

        return executor;
    }

    /**
     * Основной пул потоков для экспорта файлов
     */
    @Bean(name = "exportTaskExecutor")
    public Executor exportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(exportCorePoolSize);
        executor.setMaxPoolSize(exportMaxPoolSize);
        executor.setQueueCapacity(exportQueueCapacity);
        executor.setThreadNamePrefix(exportThreadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("Инициализирован пул потоков для экспорта: core={}, max={}, queue={}",
                exportCorePoolSize, exportMaxPoolSize, exportQueueCapacity);

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