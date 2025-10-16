package com.java.service.maintenance;

import com.java.dto.*;
import com.java.model.entity.DataCleanupHistory;
import com.java.model.entity.DataCleanupSettings;
import com.java.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${database.cleanup.auto-vacuum.enabled:true}")
    private boolean autoVacuumEnabled;

    @Value("${database.cleanup.auto-vacuum.threshold-records:1000000}")
    private long autoVacuumThresholdRecords;

    @Value("${database.cleanup.auto-vacuum.run-async:true}")
    private boolean autoVacuumAsync;

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
        log.info("Начало очистки данных до даты: {}, типы: {}, operationId: {}",
                request.getCutoffDate(), request.getEntityTypes(), request.getOperationId());

        validateCutoffDate(request.getCutoffDate());

        long startTime = System.currentTimeMillis();

        DataCleanupResultDto result = DataCleanupResultDto.builder()
                .cleanupTime(LocalDateTime.now())
                .cutoffDate(request.getCutoffDate())
                .batchSize(request.getBatchSize())
                .dryRun(request.isDryRun())
                .operationId(request.getOperationId())
                .build();

        // Отправляем начальное уведомление через WebSocket
        sendStartNotification(request.getOperationId(), request.getEntityTypes());

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

        // Автоматический VACUUM после большой очистки
        if (autoVacuumEnabled && result.isSuccess() && result.getTotalRecordsDeleted() >= autoVacuumThresholdRecords) {
            performAutoVacuum(result.getTotalRecordsDeleted(), request.getEntityTypes());
        }

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

        // Подсчитываем общее количество записей для удаления
        long totalRecordsToDelete = countRecordsToDelete("av_data", "created_at", request);

        long totalDeleted = 0;
        // ОПТИМИЗАЦИЯ: для больших объемов используем маленький batch (5000)
        int batchSize = Math.min(request.getBatchSize(), 5000);

        long startTime = System.currentTimeMillis();
        int iterationCount = 0;

        log.info("Начало пакетной очистки av_data, размер пакета: {}, всего для удаления: {} записей",
                batchSize, totalRecordsToDelete);

        // Удаляем порциями для избежания блокировок
        while (true) {
            iterationCount++;
            long iterationStart = System.currentTimeMillis();

            String sql = buildDeleteSql("av_data", "created_at", request, batchSize);
            List<Object> params = buildDeleteParams(request, "av_data");

            int deleted = jdbcTemplate.update(sql, params.toArray());
            totalDeleted += deleted;

            long iterationTime = System.currentTimeMillis() - iterationStart;
            long totalTime = System.currentTimeMillis() - startTime;
            long recordsPerSec = totalTime > 0 ? (totalDeleted * 1000L) / totalTime : 0L;

            // ВАЖНО: логируем прогресс каждые 5 итераций для мониторинга
            if (iterationCount % 5 == 0) {
                log.info("Прогресс: удалено {} записей за {} итераций ({} записей/сек), последняя итерация: {} мс",
                        totalDeleted, iterationCount, recordsPerSec, iterationTime);
            }

            // ВАЖНО: Отправляем прогресс КАЖДУЮ итерацию для real-time обновления (даже если мало итераций)
            sendProgressUpdate(request.getOperationId(), "AV_DATA", totalDeleted,
                    totalRecordsToDelete, iterationCount, recordsPerSec);

            if (deleted < batchSize) {
                log.info("Очистка av_data завершена: удалено {} записей за {} итераций, общее время: {} сек",
                        totalDeleted, iterationCount, (totalTime / 1000));

                // Отправляем финальное уведомление о завершении
                sendCompletionNotification(request.getOperationId(), "AV_DATA", totalDeleted, totalTime);
                break; // Все удалено
            }

            // Небольшая пауза между batch для снижения нагрузки на БД
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Очистка прервана пользователем после удаления {} записей", totalDeleted);
                sendErrorNotification(request.getOperationId(), "AV_DATA",
                        "Очистка прервана пользователем");
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
        List<Object> params = buildDeleteParams(request, "import_sessions");

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
        List<Object> params = buildDeleteParams(request, "export_sessions");

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
        List<Object> params = buildDeleteParams(request, "import_errors");

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
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(tableName).append(" t");

        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(request.getCutoffDate()));

        // Для таблиц без прямой связи с client_id используем JOIN через file_operations
        if (request.getExcludedClientIds() != null && !request.getExcludedClientIds().isEmpty()) {
            if (tableName.equals("import_sessions") || tableName.equals("export_sessions")) {
                sql.append(" INNER JOIN file_operations fo ON t.file_operation_id = fo.id");
                sql.append(" WHERE t.").append(dateColumn).append(" <= ?");
                sql.append(" AND fo.client_id NOT IN (")
                   .append(request.getExcludedClientIds().stream().map(String::valueOf).collect(Collectors.joining(",")))
                   .append(")");
            } else if (tableName.equals("import_errors")) {
                sql.append(" INNER JOIN import_sessions isess ON t.import_session_id = isess.id");
                sql.append(" INNER JOIN file_operations fo ON isess.file_operation_id = fo.id");
                sql.append(" WHERE t.").append(dateColumn).append(" <= ?");
                sql.append(" AND fo.client_id NOT IN (")
                   .append(request.getExcludedClientIds().stream().map(String::valueOf).collect(Collectors.joining(",")))
                   .append(")");
            } else {
                // av_data и file_operations имеют прямую связь с client_id
                sql.append(" WHERE t.").append(dateColumn).append(" <= ?");
                sql.append(" AND t.client_id NOT IN (")
                   .append(request.getExcludedClientIds().stream().map(String::valueOf).collect(Collectors.joining(",")))
                   .append(")");
            }
        } else {
            sql.append(" WHERE t.").append(dateColumn).append(" <= ?");
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
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(tableName);

        // Для таблиц без прямой связи с client_id используем подзапрос с JOIN
        if (request.getExcludedClientIds() != null && !request.getExcludedClientIds().isEmpty()) {
            if (tableName.equals("import_sessions")) {
                sql.append(" WHERE id IN (SELECT t.id FROM import_sessions t")
                   .append(" INNER JOIN file_operations fo ON t.file_operation_id = fo.id")
                   .append(" WHERE t.").append(dateColumn).append(" <= ?")
                   .append(" AND fo.client_id NOT IN (")
                   .append(request.getExcludedClientIds().stream().map(String::valueOf).collect(Collectors.joining(",")))
                   .append("))");
            } else if (tableName.equals("export_sessions")) {
                sql.append(" WHERE id IN (SELECT t.id FROM export_sessions t")
                   .append(" INNER JOIN file_operations fo ON t.file_operation_id = fo.id")
                   .append(" WHERE t.").append(dateColumn).append(" <= ?")
                   .append(" AND fo.client_id NOT IN (")
                   .append(request.getExcludedClientIds().stream().map(String::valueOf).collect(Collectors.joining(",")))
                   .append("))");
            } else if (tableName.equals("import_errors")) {
                sql.append(" WHERE id IN (SELECT t.id FROM import_errors t")
                   .append(" INNER JOIN import_sessions isess ON t.import_session_id = isess.id")
                   .append(" INNER JOIN file_operations fo ON isess.file_operation_id = fo.id")
                   .append(" WHERE t.").append(dateColumn).append(" <= ?")
                   .append(" AND fo.client_id NOT IN (")
                   .append(request.getExcludedClientIds().stream().map(String::valueOf).collect(Collectors.joining(",")))
                   .append(") LIMIT ").append(batchSize).append(")");
            } else {
                // av_data и file_operations имеют прямую связь с client_id
                sql.append(" WHERE ").append(dateColumn).append(" <= ?")
                   .append(" AND client_id NOT IN (")
                   .append(request.getExcludedClientIds().stream().map(String::valueOf).collect(Collectors.joining(",")))
                   .append(")");
            }
        } else {
            sql.append(" WHERE ").append(dateColumn).append(" <= ?");
        }

        // Для av_data с batch deletion используем более эффективный метод через CTID
        if (tableName.equals("av_data") && (request.getExcludedClientIds() == null || request.getExcludedClientIds().isEmpty())) {
            // ОПТИМИЗАЦИЯ: DELETE через CTID быстрее чем через подзапрос с ID
            sql = new StringBuilder("DELETE FROM ").append(tableName)
                .append(" WHERE ctid = ANY(ARRAY(SELECT ctid FROM ").append(tableName)
                .append(" WHERE ").append(dateColumn).append(" <= ? LIMIT ").append(batchSize).append("))");
        }

        return sql.toString();
    }

    /**
     * Построение параметров для SQL запроса
     */
    private List<Object> buildDeleteParams(DataCleanupRequestDto request, String tableName) {
        List<Object> params = new ArrayList<>();
        Timestamp cutoffTimestamp = Timestamp.valueOf(request.getCutoffDate());

        // Для всех запросов только один параметр - cutoff date
        params.add(cutoffTimestamp);

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
     * Выполнение автоматического VACUUM ANALYZE после очистки
     */
    private void performAutoVacuum(long deletedRecords, Set<String> entityTypes) {
        log.info("Запуск автоматического VACUUM ANALYZE после удаления {} записей", deletedRecords);

        if (autoVacuumAsync) {
            // Асинхронное выполнение VACUUM в отдельном потоке
            CompletableFuture.runAsync(() -> executeVacuumAnalyze(entityTypes))
                    .exceptionally(ex -> {
                        log.error("Ошибка при асинхронном выполнении VACUUM ANALYZE", ex);
                        return null;
                    });
            log.info("VACUUM ANALYZE запущен асинхронно в фоновом режиме");
        } else {
            // Синхронное выполнение VACUUM
            try {
                executeVacuumAnalyze(entityTypes);
            } catch (Exception e) {
                log.error("Ошибка при выполнении VACUUM ANALYZE", e);
            }
        }
    }

    /**
     * Выполнение VACUUM ANALYZE для таблиц
     */
    private void executeVacuumAnalyze(Set<String> entityTypes) {
        long startTime = System.currentTimeMillis();
        int vacuumedTables = 0;

        if (entityTypes == null || entityTypes.isEmpty()) {
            entityTypes = Set.of("AV_DATA");
        }

        for (String entityType : entityTypes) {
            String tableName = getTableNameForEntityType(entityType);
            if (tableName != null) {
                try {
                    log.info("Выполнение VACUUM ANALYZE для таблицы: {}", tableName);
                    long tableStartTime = System.currentTimeMillis();

                    jdbcTemplate.execute("VACUUM ANALYZE " + tableName);

                    long tableTime = System.currentTimeMillis() - tableStartTime;
                    log.info("VACUUM ANALYZE завершен для {}: {} мс", tableName, tableTime);
                    vacuumedTables++;

                } catch (Exception e) {
                    log.warn("Не удалось выполнить VACUUM ANALYZE для {}: {}", tableName, e.getMessage());
                }
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Автоматический VACUUM ANALYZE завершен: обработано {} таблиц за {} сек",
                vacuumedTables, totalTime / 1000);
    }

    /**
     * Получение имени таблицы для типа сущности
     */
    private String getTableNameForEntityType(String entityType) {
        switch (entityType.toUpperCase()) {
            case "AV_DATA":
                return "av_data";
            case "IMPORT_SESSIONS":
                return "import_sessions";
            case "EXPORT_SESSIONS":
                return "export_sessions";
            case "IMPORT_ERRORS":
                return "import_errors";
            case "FILE_OPERATIONS":
                return "file_operations";
            default:
                log.warn("Неизвестный тип сущности для VACUUM: {}", entityType);
                return null;
        }
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

    /**
     * Отправка начального уведомления о старте очистки
     * ВАЖНО: Используем separate thread для обхода блокировки @Transactional
     */
    private void sendStartNotification(String operationId, Set<String> entityTypes) {
        if (operationId == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                DataCleanupProgressDto progress = DataCleanupProgressDto.builder()
                        .operationId(operationId)
                        .entityType(entityTypes != null ? String.join(", ", entityTypes) : "AV_DATA")
                        .message("Начинаем очистку данных...")
                        .percentage(0)
                        .processedRecords(0L)
                        .totalRecords(0L)
                        .currentIteration(0)
                        .recordsPerSecond(0L)
                        .status("IN_PROGRESS")
                        .timestamp(LocalDateTime.now())
                        .build();

                messagingTemplate.convertAndSend("/topic/cleanup-progress/" + operationId, progress);
                log.debug("WebSocket старт отправлен для operationId: {}", operationId);
            } catch (Exception e) {
                log.error("Ошибка отправки стартового уведомления для операции {}", operationId, e);
            }
        });
    }

    /**
     * Отправка прогресса очистки через WebSocket
     * ВАЖНО: Используем separate thread для обхода блокировки @Transactional
     */
    private void sendProgressUpdate(String operationId, String entityType, long processedRecords,
                                     long totalRecords, int iteration, long recordsPerSec) {
        if (operationId == null) return;

        // Отправляем в отдельном потоке, чтобы обойти блокировку транзакции
        CompletableFuture.runAsync(() -> {
            try {
                int percentage = totalRecords > 0 ? (int) ((processedRecords * 100L) / totalRecords) : 0;

                DataCleanupProgressDto progress = DataCleanupProgressDto.builder()
                        .operationId(operationId)
                        .entityType(entityType)
                        .message(String.format("Обработано %,d из %,d записей", processedRecords, totalRecords))
                        .percentage(percentage)
                        .processedRecords(processedRecords)
                        .totalRecords(totalRecords)
                        .currentIteration(iteration)
                        .recordsPerSecond(recordsPerSec)
                        .status("IN_PROGRESS")
                        .timestamp(LocalDateTime.now())
                        .build();

                messagingTemplate.convertAndSend("/topic/cleanup-progress/" + operationId, progress);
                log.debug("WebSocket прогресс отправлен: {}%", percentage);
            } catch (Exception e) {
                log.error("Ошибка отправки прогресса для операции {}", operationId, e);
            }
        });
    }

    /**
     * Отправка уведомления о завершении очистки
     * ВАЖНО: Используем separate thread для обхода блокировки @Transactional
     */
    private void sendCompletionNotification(String operationId, String entityType,
                                            long totalDeleted, long executionTimeMs) {
        if (operationId == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                DataCleanupProgressDto progress = DataCleanupProgressDto.builder()
                        .operationId(operationId)
                        .entityType(entityType)
                        .message(String.format("Очистка завершена: удалено %,d записей за %d сек",
                                totalDeleted, executionTimeMs / 1000))
                        .percentage(100)
                        .processedRecords(totalDeleted)
                        .totalRecords(totalDeleted)
                        .recordsPerSecond(executionTimeMs > 0 ? (totalDeleted * 1000L) / executionTimeMs : 0L)
                        .status("COMPLETED")
                        .timestamp(LocalDateTime.now())
                        .build();

                messagingTemplate.convertAndSend("/topic/cleanup-progress/" + operationId, progress);
                log.debug("WebSocket завершение отправлено для operationId: {}", operationId);
            } catch (Exception e) {
                log.error("Ошибка отправки уведомления о завершении для операции {}", operationId, e);
            }
        });
    }

    /**
     * Отправка уведомления об ошибке
     * ВАЖНО: Используем separate thread для обхода блокировки @Transactional
     */
    private void sendErrorNotification(String operationId, String entityType, String errorMessage) {
        if (operationId == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                DataCleanupProgressDto progress = DataCleanupProgressDto.builder()
                        .operationId(operationId)
                        .entityType(entityType)
                        .message("Ошибка при очистке данных")
                        .status("ERROR")
                        .errorMessage(errorMessage)
                        .timestamp(LocalDateTime.now())
                        .build();

                messagingTemplate.convertAndSend("/topic/cleanup-progress/" + operationId, progress);
                log.debug("WebSocket ошибка отправлена для operationId: {}", operationId);
            } catch (Exception e) {
                log.error("Ошибка отправки уведомления об ошибке для операции {}", operationId, e);
            }
        });
    }
}
