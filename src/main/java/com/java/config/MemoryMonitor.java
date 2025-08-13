package com.java.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Монитор использования памяти для контроля ресурсов при импорте
 */
@Component
@Slf4j
public class MemoryMonitor {

    private final ImportConfig.ImportSettings importSettings;

    public MemoryMonitor(ImportConfig.ImportSettings importSettings) {
        this.importSettings = importSettings;
    }

    /**
     * Проверяет доступность памяти для операции
     */
    public boolean isMemoryAvailable() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long availableMemory = maxMemory - usedMemory;

        // УЖЕСТОЧАЕМ проверку для домашнего ПК
        long threshold = maxMemory / 2; // 50% вместо текущих настроек

        if (availableMemory < threshold) {
            log.warn("Низкий уровень памяти: {}MB свободно из {}MB",
                    availableMemory / (1024 * 1024), maxMemory / (1024 * 1024));
            return false;
        }
        return true;
    }

    /**
     * Получает информацию об использовании памяти
     */
    public MemoryInfo getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;

        return new MemoryInfo(
                usedMemory / (1024 * 1024),
                totalMemory / (1024 * 1024),
                maxMemory / (1024 * 1024),
                (usedMemory * 100) / maxMemory
        );
    }

    /**
     * Информация о памяти
     */
    public static class MemoryInfo {
        private final long usedMB;
        private final long totalMB;
        private final long maxMB;
        private final long usagePercentage;

        public MemoryInfo(long usedMB, long totalMB, long maxMB, long usagePercentage) {
            this.usedMB = usedMB;
            this.totalMB = totalMB;
            this.maxMB = maxMB;
            this.usagePercentage = usagePercentage;
        }

        // Getters
        public long getUsedMB() { return usedMB; }
        public long getTotalMB() { return totalMB; }
        public long getMaxMB() { return maxMB; }
        public long getUsagePercentage() { return usagePercentage; }

        @Override
        public String toString() {
            return String.format("Memory: %d/%d MB (%d%%)", usedMB, maxMB, usagePercentage);
        }
    }
}