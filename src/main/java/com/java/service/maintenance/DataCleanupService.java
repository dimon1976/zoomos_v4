package com.java.service.maintenance;

import com.java.dto.*;
import com.java.model.entity.DataCleanupHistory;
import com.java.model.entity.DataCleanupSettings;
import com.java.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для управления очисткой устаревших данных
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataCleanupService {

    private final JdbcTemplate jdbcTemplate;
    private final DataCleanupSettingsRepository settingsRepository;
    private final DataCleanupHistoryRepository historyRepository;
    private final ClientRepository clientRepository;

    private static final int MIN_RETENTION_DAYS = 7; // Минимум 7 дней для безопасности
    private static final long BYTES_PER_RECORD_ESTIMATE = 1024; // Примерная оценка размера записи

    /**
     * Предпросмотр очистки - показывает что будет удалено БЕЗ фактического удаления
     */
    public DataCleanupPreviewDto previewCleanup(DataCleanupRequestDto request) {
        log.info("Предпросмотр очистки данных до даты: {}", request.getCutoffDate());

        validateCutoffDate(request.getCutoffDate());

        DataCleanupPreviewDto preview = DataCleanupPreviewDto.builder()
                .cutoffDate(request.getCutoffDate())
                .build();

        try {
            Set<String> entityTypes = request.getEntityTypes();
            if (entityTypes == null || entityTypes.isEmpty()) {
                entityTypes = Set.of("AV_DATA");
            }

            for (String entityType : entityTypes) {
                switch (entityType.toUpperCase()) {
                    case "AV_DATA":
                        previewAvDataCleanup(request, preview);
                        break;
                    case "IMPORT_SESSIONS":
                        previewImportSessionsCleanup(request, preview);
                        break;
                    case "EXPORT_SESSIONS":
                        previewExportSessionsCleanup(request, preview);
                        break;
                    case "IMPORT_ERRORS":
                        previewImportErrorsCleanup(request, preview);
                        break;
                    case "FILE_OPERATIONS":
                        previewFileOperationsCleanup(request, preview);
                        break;
                }
            }

            // Оценка освобождаемого места
            preview.setEstimatedFreeSpaceBytes(preview.getTotalRecordsToDelete() * BYTES_PER_RECORD_ESTIMATE);
            preview.setFormattedEstimatedSpace(formatBytes(preview.getEstimatedFreeSpaceBytes()));

            // Добавляем предупреждения
            addWarnings(preview, request);

        } catch (Exception e) {
            log.error("Ошибка при предпросмотре очистки", e);
            preview.addWarning("Ошибка при подсчете: " + e.getMessage());
        }

        return preview;
    }

    /**
     * Выполняет очистку данных по заданным критериям
     */
    @Transactional
    public DataCleanupResultDto executeCleanup(DataCleanupRequestDto request) {
        log.info("Начало очистки данных до даты: {}, типы: {}",
                request.getCutoffDate(), request.getEntityTypes());

        validateCutoffDate(request.getCutoffDate());

        long startTime = System.currentTimeMillis();

        DataCleanupResultDto result = DataCleanupResultDto.builder()
                .cleanupTime(LocalDateTime.now())
                .cutoffDate(request.getCutoffDate())
                .batchSize(request.getBatchSize())
                .dryRun(request.isDryRun())
                .build();

        try {
            Set<String> entityTypes = request.getEntityTypes();
            if (entityTypes == null || entityTypes.isEmpty()) {
                entityTypes = Set.of("AV_DATA");
            }

            for (String entityType : entityTypes) {
                try {
                    long deleted = cleanupByEntityType(entityType, request);
                    result.addDeletedRecords(entityType, deleted);

                    // Сохраняем в историю
                    saveCleanupHistory(entityType, request, deleted, "SUCCESS", null);

                } catch (Exception e) {
                    log.error("Ошибка при очистке {}: {}", entityType, e.getMessage(), e);
                    result.addDeletedRecords(entityType, 0L);
                    saveCleanupHistory(entityType, request, 0L, "FAILED", e.getMessage());
                }
            }

            result.setSuccess(true);
            result.setFreedSpaceBytes(result.getTotalRecordsDeleted() * BYTES_PER_RECORD_ESTIMATE);
            result.setFormattedFreedSpace(formatBytes(result.getFreedSpaceBytes()));

        } catch (Exception e) {
            log.error("Критическая ошибка при очистке данных", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        result.setExecutionTimeMs(System.currentTimeMillis() - startTime);

        log.info("Очистка завершена: удалено {} записей за {} мс",
                result.getTotalRecordsDeleted(), result.getExecutionTimeMs());

        return result;
    }

    /**
     * Очистка данных по типу сущности
     */
    private long cleanupByEntityType(String entityType, DataCleanupRequestDto request) {
        switch (entityType.toUpperCase()) {
            case "AV_DATA":
                return cleanupAvData(request);
            case "IMPORT_SESSIONS":
                return cleanupImportSessions(request);
            case "EXPORT_SESSIONS":
                return cleanupExportSessions(request);
            case "IMPORT_ERRORS":
                return cleanupImportErrors(request);
            case "FILE_OPERATIONS":
                return cleanupFileOperations(request);
            default:
                log.warn("Неизвестный тип данных для очистки: {}", entityType);
                return 0;
        }
    }

    /**
     * Очистка av_data (основная таблица с сырыми данными)
     */
    private long cleanupAvData(DataCleanupRequestDto request) {
        log.info("Очистка av_data до даты: {}", request.getCutoffDate());

        if (request.isDryRun()) {
            return countRecordsToDelete("av_data", "created_at", request);
        }

        long totalDeleted = 0;
        int batchSize = request.getBatchSize();
        Set<Long> excludedClients = request.getExcludedClientIds();

        // Удаляем порциями для избежания блокировок
        while (true) {
            String sql = buildDeleteSql("av_data", "created_at", request, batchSize);
            List<Object> params = buildDeleteParams(request);

            int deleted = jdbcTemplate.update(sql, params.toArray());
            totalDeleted += deleted;

            log.debug("Удалено {} записей из av_data, всего: {}", deleted, totalDeleted);

            if (deleted < batchSize) {
                break; // Все удалено
            }

            // Небольшая пауза между batch для снижения нагрузки
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return totalDeleted;
    }

    /**
     * Очистка сессий импорта
     */
    private long cleanupImportSessions(DataCleanupRequestDto request) {
        log.info("Очистка import_sessions до даты: {}", request.getCutoffDate());

        if (request.isDryRun()) {
            return countRecordsToDelete("import_sessions", "started_at", request);
        }

        String sql = buildDeleteSql("import_sessions", "started_at", request, request.getBatchSize());
        List<Object> params = buildDeleteParams(request);

        return jdbcTemplate.update(sql, params.toArray());
    }

    /**
     * Очистка сессий экспорта (с учетом что статистику хранить долго)
     */
    private long cleanupExportSessions(DataCleanupRequestDto request) {
        log.info("Очистка export_sessions до даты: {}", request.getCutoffDate());

        if (request.isDryRun()) {
            return countRecordsToDelete("export_sessions", "started_at", request);
        }

        String sql = buildDeleteSql("export_sessions", "started_at", request, request.getBatchSize());
        List<Object> params = buildDeleteParams(request);

        return jdbcTemplate.update(sql, params.toArray());
    }

    /**
     * Очистка ошибок импорта
     */
    private long cleanupImportErrors(DataCleanupRequestDto request) {
        log.info("Очистка import_errors до даты: {}", request.getCutoffDate());

        if (request.isDryRun()) {
            return countRecordsToDelete("import_errors", "occurred_at", request);
        }

        String sql = buildDeleteSql("import_errors", "occurred_at", request, request.getBatchSize());
        List<Object> params = buildDeleteParams(request);

        return jdbcTemplate.update(sql, params.toArray());
    }

    /**
     * Очистка файловых операций
     */
    private long cleanupFileOperations(DataCleanupRequestDto request) {
        log.info("Очистка file_operations до даты: {}", request.getCutoffDate());

        if (request.isDryRun()) {
            String sql = "SELECT COUNT(*) FROM file_operations WHERE started_at <= ? AND status IN ('COMPLETED', 'FAILED')";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, Timestamp.valueOf(request.getCutoffDate()));
            return count != null ? count : 0;
        }

        String sql = "DELETE FROM file_operations WHERE started_at <= ? AND status IN ('COMPLETED', 'FAILED')";
        if (request.getExcludedClientIds() != null && !request.getExcludedClientIds().isEmpty()) {
            sql += " AND client_id NOT IN (" +
                   request.getExcludedClientIds().stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
        }

        return jdbcTemplate.update(sql, Timestamp.valueOf(request.getCutoffDate()));
    }

    /**
     * Предпросмотр очистки av_data
     */
    private void previewAvDataCleanup(DataCleanupRequestDto request, DataCleanupPreviewDto preview) {
        long count = countRecordsToDelete("av_data", "created_at", request);
        preview.addRecordsToDelete("AV_DATA", count);

        // Получаем диапазон дат
        String sql = "SELECT MIN(created_at), MAX(created_at) FROM av_data WHERE created_at <= ?";
        jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                Timestamp min = rs.getTimestamp(1);
                Timestamp max = rs.getTimestamp(2);
                if (min != null && max != null) {
                    preview.getDateRanges().put("AV_DATA",
                        new DataCleanupPreviewDto.DateRange(min.toLocalDateTime(), max.toLocalDateTime()));
                }
            }
        }, Timestamp.valueOf(request.getCutoffDate()));

        // Группировка по клиентам
        Map<String, Long> byClient = getRecordsByClient("av_data", "created_at", request);
        preview.getRecordsByClient().put("AV_DATA", byClient);
    }

    /**
     * Предпросмотр очистки сессий импорта
     */
    private void previewImportSessionsCleanup(DataCleanupRequestDto request, DataCleanupPreviewDto preview) {
        long count = countRecordsToDelete("import_sessions", "started_at", request);
        preview.addRecordsToDelete("IMPORT_SESSIONS", count);
    }

    /**
     * Предпросмотр очистки сессий экспорта
     */
    private void previewExportSessionsCleanup(DataCleanupRequestDto request, DataCleanupPreviewDto preview) {
        long count = countRecordsToDelete("export_sessions", "started_at", request);
        preview.addRecordsToDelete("EXPORT_SESSIONS", count);
    }

    /**
     * Предпросмотр очистки ошибок импорта
     */
    private void previewImportErrorsCleanup(DataCleanupRequestDto request, DataCleanupPreviewDto preview) {
        long count = countRecordsToDelete("import_errors", "occurred_at", request);
        preview.addRecordsToDelete("IMPORT_ERRORS", count);
    }

    /**
     * Предпросмотр очистки файловых операций
     */
    private void previewFileOperationsCleanup(DataCleanupRequestDto request, DataCleanupPreviewDto preview) {
        String sql = "SELECT COUNT(*) FROM file_operations WHERE started_at <= ? AND status IN ('COMPLETED', 'FAILED')";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, Timestamp.valueOf(request.getCutoffDate()));
        preview.addRecordsToDelete("FILE_OPERATIONS", count != null ? count : 0);
    }

    /**
     * Подсчет записей для удаления
     */
    private long countRecordsToDelete(String tableName, String dateColumn, DataCleanupRequestDto request) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(tableName)
                .append(" WHERE ").append(dateColumn).append(" <= ?");

        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(request.getCutoffDate()));

        if (request.getExcludedClientIds() != null && !request.getExcludedClientIds().isEmpty()) {
            sql.append(" AND client_id NOT IN (")
               .append(request.getExcludedClientIds().stream().map(String::valueOf).collect(Collectors.joining(",")))
               .append(")");
        }

        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    /**
     * Группировка записей по клиентам
     */
    private Map<String, Long> getRecordsByClient(String tableName, String dateColumn, DataCleanupRequestDto request) {
        String sql = "SELECT c.name, COUNT(*) FROM " + tableName + " t " +
                    "LEFT JOIN clients c ON t.client_id = c.id " +
                    "WHERE t." + dateColumn + " <= ? GROUP BY c.name";

        Map<String, Long> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            String clientName = rs.getString(1);
            long count = rs.getLong(2);
            result.put(clientName != null ? clientName : "Без клиента", count);
        }, Timestamp.valueOf(request.getCutoffDate()));

        return result;
    }

    /**
     * Построение SQL для удаления
     */
    private String buildDeleteSql(String tableName, String dateColumn, DataCleanupRequestDto request, int batchSize) {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(tableName)
                .append(" WHERE ").append(dateColumn).append(" <= ?");

        if (request.getExcludedClientIds() != null && !request.getExcludedClientIds().isEmpty()) {
            sql.append(" AND client_id NOT IN (")
               .append(request.getExcludedClientIds().stream().map(String::valueOf).collect(Collectors.joining(",")))
               .append(")");
        }

        // Для таблиц с большим количеством записей используем LIMIT
        if (tableName.equals("av_data") || tableName.equals("import_errors")) {
            sql.append(" AND id IN (SELECT id FROM ").append(tableName)
               .append(" WHERE ").append(dateColumn).append(" <= ? LIMIT ").append(batchSize).append(")");
        }

        return sql.toString();
    }

    /**
     * Построение параметров для SQL запроса
     */
    private List<Object> buildDeleteParams(DataCleanupRequestDto request) {
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(request.getCutoffDate()));
        return params;
    }

    /**
     * Сохранение истории очистки
     */
    private void saveCleanupHistory(String entityType, DataCleanupRequestDto request,
                                    long recordsDeleted, String status, String errorMessage) {
        try {
            DataCleanupHistory history = DataCleanupHistory.builder()
                    .entityType(entityType)
                    .cutoffDate(request.getCutoffDate())
                    .recordsDeleted(recordsDeleted)
                    .status(status)
                    .errorMessage(errorMessage)
                    .initiatedBy(request.getInitiatedBy())
                    .batchSize(request.getBatchSize())
                    .excludedClientIds(request.getExcludedClientIds() != null ?
                            request.getExcludedClientIds().toString() : null)
                    .build();

            historyRepository.save(history);
        } catch (Exception e) {
            log.error("Ошибка при сохранении истории очистки", e);
        }
    }

    /**
     * Добавление предупреждений
     */
    private void addWarnings(DataCleanupPreviewDto preview, DataCleanupRequestDto request) {
        if (preview.getTotalRecordsToDelete() == 0) {
            preview.addWarning("Нет данных для удаления на указанную дату");
        }

        if (preview.getTotalRecordsToDelete() > 1000000) {
            preview.addWarning("Большое количество записей (>1млн) - операция может занять длительное время");
        }

        LocalDateTime minAllowedDate = LocalDateTime.now().minusDays(MIN_RETENTION_DAYS);
        if (request.getCutoffDate().isAfter(minAllowedDate)) {
            preview.addWarning("Попытка удаления данных моложе " + MIN_RETENTION_DAYS + " дней - операция заблокирована");
        }
    }

    /**
     * Валидация даты отсечки
     */
    private void validateCutoffDate(LocalDateTime cutoffDate) {
        if (cutoffDate == null) {
            throw new IllegalArgumentException("Дата отсечки не может быть null");
        }

        LocalDateTime minAllowedDate = LocalDateTime.now().minusDays(MIN_RETENTION_DAYS);
        if (cutoffDate.isAfter(minAllowedDate)) {
            throw new IllegalArgumentException(
                    "Невозможно удалить данные моложе " + MIN_RETENTION_DAYS + " дней. " +
                    "Минимально допустимая дата: " + minAllowedDate);
        }

        if (cutoffDate.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Дата отсечки не может быть в будущем");
        }
    }

    /**
     * Получение настроек очистки
     */
    public List<DataCleanupSettingsDto> getCleanupSettings() {
        return settingsRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Обновление настроек очистки
     */
    @Transactional
    public DataCleanupSettingsDto updateCleanupSettings(DataCleanupSettingsDto dto) {
        DataCleanupSettings settings = settingsRepository.findByEntityType(dto.getEntityType())
                .orElseThrow(() -> new IllegalArgumentException("Настройки не найдены для типа: " + dto.getEntityType()));

        settings.setRetentionDays(dto.getRetentionDays());
        settings.setAutoCleanupEnabled(dto.getAutoCleanupEnabled());
        settings.setCleanupBatchSize(dto.getCleanupBatchSize());
        settings.setDescription(dto.getDescription());

        return toDto(settingsRepository.save(settings));
    }

    /**
     * Получение истории очисток
     */
    public List<DataCleanupHistory> getCleanupHistory(int limit) {
        return historyRepository.findTop20ByOrderByCleanupDateDesc();
    }

    /**
     * Преобразование Entity в DTO
     */
    private DataCleanupSettingsDto toDto(DataCleanupSettings entity) {
        return DataCleanupSettingsDto.builder()
                .id(entity.getId())
                .entityType(entity.getEntityType())
                .retentionDays(entity.getRetentionDays())
                .autoCleanupEnabled(entity.getAutoCleanupEnabled())
                .cleanupBatchSize(entity.getCleanupBatchSize())
                .description(entity.getDescription())
                .build();
    }

    /**
     * Форматирование размера в байтах
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
