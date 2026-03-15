package com.java.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

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
        log.info("Создание importTaskExecutor с параметрами: core={}, max={}, queue={}, prefix='{}'",
                importCorePoolSize, importMaxPoolSize, importQueueCapacity, importThreadNamePrefix);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, importCorePoolSize));
        executor.setMaxPoolSize(Math.max(1, importMaxPoolSize));
        executor.setQueueCapacity(Math.max(0, importQueueCapacity));
        executor.setThreadNamePrefix(importThreadNamePrefix != null ? importThreadNamePrefix : "ImportExecutor-");
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
        log.info("Создание exportTaskExecutor с параметрами: core={}, max={}, queue={}, prefix='{}'",
                exportCorePoolSize, exportMaxPoolSize, exportQueueCapacity, exportThreadNamePrefix);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, exportCorePoolSize));
        executor.setMaxPoolSize(Math.max(1, exportMaxPoolSize));
        executor.setQueueCapacity(Math.max(0, exportQueueCapacity));
        executor.setThreadNamePrefix(exportThreadNamePrefix != null ? exportThreadNamePrefix : "ExportExecutor-");
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

    /**
     * Пул потоков для утилит (долгосрочные операции)
     */
    @Bean(name = "utilsTaskExecutor")
    public Executor utilsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("UtilsExecutor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300); // 5 минут на завершение
        executor.initialize();

        log.info("Инициализирован пул потоков для утилит: core=1, max=2, queue=10");

        return executor;
    }

    /**
     * Пул потоков для обработки редиректов (IO-интенсивные операции)
     */
    @Bean(name = "redirectTaskExecutor")
    public Executor redirectTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("RedirectExecutor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(600); // 10 минут на завершение для долгих операций
        executor.initialize();

        log.info("Инициализирован пул потоков для редиректов: core=1, max=3, queue=25");

        return executor;
    }

    /**
     * Пул потоков для очистки данных (долгосрочные операции с БД)
     */
    @Bean(name = "cleanupTaskExecutor")
    public Executor cleanupTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("CleanupExecutor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(1800); // 30 минут на завершение для очень долгих операций
        executor.initialize();

        log.info("Инициализирован пул потоков для очистки данных: core=1, max=2, queue=10");

        return executor;
    }

    /**
     * Пул потоков для параллельных проверок выкачки Zoomos (Playwright IO-тяжёлые операции)
     */
    @Bean(name = "zoomosCheckExecutor")
    public Executor zoomosCheckExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("zoomos-check-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(600);
        executor.initialize();
        return executor;
    }

    /**
     * Планировщик задач для обслуживания системы (cron-расписания)
     */
    @Bean(name = "maintenanceTaskScheduler")
    public ThreadPoolTaskScheduler maintenanceTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("maintenance-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * Планировщик задач для автоматических проверок Zoomos (cron-расписания)
     */
    @Bean(name = "zoomosSchedulerTaskScheduler")
    public ThreadPoolTaskScheduler zoomosSchedulerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("zoomos-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }
}