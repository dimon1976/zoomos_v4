package com.java.service.operations;

import com.java.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для безопасного удаления операций со всеми связанными данными
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OperationDeletionService {

    private final FileOperationRepository fileOperationRepository;
    private final ImportSessionRepository importSessionRepository;
    private final ExportSessionRepository exportSessionRepository;
    private final ImportErrorRepository importErrorRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final ExportStatisticsRepository exportStatisticsRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Удаляет операцию со всеми связанными данными
     * Порядок удаления критически важен для сохранения целостности данных
     */
    @Transactional
    public void deleteOperationCompletely(Long operationId) {
        log.info("Начинаем полное удаление операции ID: {}", operationId);

        if (!fileOperationRepository.existsById(operationId)) {
            throw new IllegalArgumentException("Операция не найдена");
        }

        try {
            // 1. Находим сессию импорта если она есть
            importSessionRepository.findByFileOperationId(operationId).ifPresent(importSession -> {
                log.debug("Удаляем данные импорта для сессии: {}", importSession.getId());
                
                // 1.1. Удаляем импортированные данные из av_data (по operation_id)
                int deletedAvData = jdbcTemplate.update(
                    "DELETE FROM av_data WHERE operation_id = ?", 
                    operationId
                );
                log.debug("Удалено записей из av_data: {}", deletedAvData);

                // 1.2. Удаляем данные из av_handbook (по import_session_id)
                int deletedAvHandbook = jdbcTemplate.update(
                    "DELETE FROM av_handbook WHERE import_session_id = ?", 
                    importSession.getId()
                );
                log.debug("Удалено записей из av_handbook: {}", deletedAvHandbook);

                // 1.3. Удаляем ошибки импорта (CASCADE удалит автоматически, но лучше явно)
                importErrorRepository.deleteByImportSessionId(importSession.getId());

                // 1.4. Удаляем метаданные файла (CASCADE удалит автоматически, но лучше явно)
                fileMetadataRepository.findByImportSessionId(importSession.getId())
                    .ifPresent(fileMetadataRepository::delete);

                // 1.5. Удаляем саму сессию импорта
                importSessionRepository.delete(importSession);
            });

            // 2. Находим сессию экспорта если она есть
            exportSessionRepository.findByFileOperationId(operationId).ifPresent(exportSession -> {
                log.debug("Удаляем данные экспорта для сессии: {}", exportSession.getId());

                // 2.1. Удаляем статистику экспорта
                exportStatisticsRepository.deleteByExportSessionId(exportSession.getId());
                log.debug("Удалена статистика экспорта для сессии: {}", exportSession.getId());

                // 2.2. Удаляем саму сессию экспорта
                exportSessionRepository.delete(exportSession);
            });

            // 3. Удаляем основную запись операции
            fileOperationRepository.deleteById(operationId);

            log.info("Операция {} успешно удалена со всеми связанными данными", operationId);

        } catch (Exception e) {
            log.error("Ошибка при удалении операции {}: {}", operationId, e.getMessage(), e);
            throw new RuntimeException("Не удалось удалить операцию: " + e.getMessage(), e);
        }
    }

    /**
     * Проверяет сколько связанных записей будет удалено
     * Полезно для предварительной оценки объёма удаления
     */
    public DeletionStatistics getDeletionStatistics(Long operationId) {
        log.debug("Подсчитываем статистику удаления для операции: {}", operationId);

        DeletionStatistics stats = new DeletionStatistics();
        stats.setOperationId(operationId);

        // Подсчёт данных импорта
        importSessionRepository.findByFileOperationId(operationId).ifPresent(importSession -> {
            // Количество записей в av_data
            Integer avDataCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM av_data WHERE operation_id = ?", 
                Integer.class, operationId
            );
            stats.setAvDataRecords(avDataCount != null ? avDataCount : 0);

            // Количество записей в av_handbook
            Integer avHandbookCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM av_handbook WHERE import_session_id = ?", 
                Integer.class, importSession.getId()
            );
            stats.setAvHandbookRecords(avHandbookCount != null ? avHandbookCount : 0);

            // Количество ошибок
            stats.setImportErrors(importErrorRepository.countByImportSessionId(importSession.getId()));

            stats.setHasImportSession(true);
        });

        // Подсчёт данных экспорта
        exportSessionRepository.findByFileOperationId(operationId).ifPresent(exportSession -> {
            stats.setExportStatistics((int) exportStatisticsRepository.countByExportSessionId(exportSession.getId()));
            stats.setHasExportSession(true);
        });

        return stats;
    }

    /**
     * Статистика предстоящего удаления
     */
    public static class DeletionStatistics {
        private Long operationId;
        private int avDataRecords = 0;
        private int avHandbookRecords = 0;
        private long importErrors = 0;
        private int exportStatistics = 0;
        private boolean hasImportSession = false;
        private boolean hasExportSession = false;

        // Getters and Setters
        public Long getOperationId() { return operationId; }
        public void setOperationId(Long operationId) { this.operationId = operationId; }

        public int getAvDataRecords() { return avDataRecords; }
        public void setAvDataRecords(int avDataRecords) { this.avDataRecords = avDataRecords; }

        public int getAvHandbookRecords() { return avHandbookRecords; }
        public void setAvHandbookRecords(int avHandbookRecords) { this.avHandbookRecords = avHandbookRecords; }

        public long getImportErrors() { return importErrors; }
        public void setImportErrors(long importErrors) { this.importErrors = importErrors; }

        public int getExportStatistics() { return exportStatistics; }
        public void setExportStatistics(int exportStatistics) { this.exportStatistics = exportStatistics; }

        public boolean isHasImportSession() { return hasImportSession; }
        public void setHasImportSession(boolean hasImportSession) { this.hasImportSession = hasImportSession; }

        public boolean isHasExportSession() { return hasExportSession; }
        public void setHasExportSession(boolean hasExportSession) { this.hasExportSession = hasExportSession; }

        public int getTotalRecords() {
            return avDataRecords + avHandbookRecords + (int) importErrors + exportStatistics;
        }

        @Override
        public String toString() {
            return String.format("DeletionStatistics{operationId=%d, avData=%d, avHandbook=%d, errors=%d, exportStats=%d, total=%d}", 
                operationId, avDataRecords, avHandbookRecords, importErrors, exportStatistics, getTotalRecords());
        }
    }
}