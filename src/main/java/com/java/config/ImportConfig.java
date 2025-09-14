package com.java.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.java.mapper.FileMetadataMapper;
import com.java.mapper.ExportTemplateMapper;
import com.java.mapper.ExportSessionMapper;
import javax.annotation.PostConstruct;

/**
 * Конфигурация параметров импорта
 */
@Configuration
public class ImportConfig {

    @Value("${import.batch-size:1000}")
    private int batchSize;

    @Value("${import.max-memory-percentage:60}")
    private int maxMemoryPercentage;

    @Value("${import.file-analysis.sample-rows:100}")
    private int sampleRows;

    @Value("${import.timeout-minutes:60}")
    private int timeoutMinutes;

    @Bean
    public ImportSettings importSettings() {
        return ImportSettings.builder()
                .batchSize(batchSize)
                .maxMemoryPercentage(maxMemoryPercentage)
                .sampleRows(sampleRows)
                .timeoutMinutes(timeoutMinutes)
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @PostConstruct
    public void initializeStaticMappers() {
        ObjectMapper mapper = objectMapper();
        FileMetadataMapper.setObjectMapper(mapper);
        ExportTemplateMapper.setObjectMapper(mapper);
        ExportSessionMapper.setObjectMapper(mapper);
    }

    /**
     * Настройки импорта
     */
    public static class ImportSettings {
        private final int batchSize;
        private final int maxMemoryPercentage;
        private final int sampleRows;
        private final int timeoutMinutes;

        private ImportSettings(int batchSize, int maxMemoryPercentage,
                               int sampleRows, int timeoutMinutes) {
            this.batchSize = batchSize;
            this.maxMemoryPercentage = maxMemoryPercentage;
            this.sampleRows = sampleRows;
            this.timeoutMinutes = timeoutMinutes;
        }

        public static ImportSettingsBuilder builder() {
            return new ImportSettingsBuilder();
        }

        // Getters
        public int getBatchSize() { return batchSize; }
        public int getMaxMemoryPercentage() { return maxMemoryPercentage; }
        public int getSampleRows() { return sampleRows; }
        public int getTimeoutMinutes() { return timeoutMinutes; }

        public long getMaxMemoryBytes() {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            return (maxMemory * maxMemoryPercentage) / 100;
        }

        public static class ImportSettingsBuilder {
            private int batchSize = 1000;
            private int maxMemoryPercentage = 60;
            private int sampleRows = 100;
            private int timeoutMinutes = 60;

            public ImportSettingsBuilder batchSize(int batchSize) {
                this.batchSize = batchSize;
                return this;
            }

            public ImportSettingsBuilder maxMemoryPercentage(int percentage) {
                this.maxMemoryPercentage = percentage;
                return this;
            }

            public ImportSettingsBuilder sampleRows(int rows) {
                this.sampleRows = rows;
                return this;
            }

            public ImportSettingsBuilder timeoutMinutes(int minutes) {
                this.timeoutMinutes = minutes;
                return this;
            }

            public ImportSettings build() {
                return new ImportSettings(batchSize, maxMemoryPercentage,
                        sampleRows, timeoutMinutes);
            }
        }
    }
}
