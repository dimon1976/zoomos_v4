package com.java.service.maintenance;

import com.java.dto.*;
import com.java.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import javax.sql.DataSource;

@Service
@Slf4j
@RequiredArgsConstructor
public class DatabaseMaintenanceService {

    private final EntityManager entityManager;
    private final DataSource dataSource;

    @Value("${database.maintenance.performance.slow-query-threshold:1000}")
    private long slowQueryThresholdMs;

    // Кэшируем результат проверки наличия pg_stat_statements
    private volatile Boolean pgStatStatementsAvailable = null;

    public Map<String, Object> performVacuumFull() {
        log.info("Запуск VACUUM FULL для всех таблиц");
        Map<String, Object> result = new HashMap<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // VACUUM требует autoCommit=true (не может выполняться в транзакции)
            connection.setAutoCommit(true);

            long startSize = getDatabaseSizeViaJdbc(statement);

            // Получаем список всех таблиц
            List<String> tables = new ArrayList<>();
            try (ResultSet tablesRs = statement.executeQuery(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'")) {
                while (tablesRs.next()) {
                    tables.add(tablesRs.getString("tablename"));
                }
            }

            // VACUUM FULL для каждой таблицы
            for (String table : tables) {
                log.info("VACUUM FULL для таблицы: {}", table);
                try {
                    // Валидация имени таблицы для предотвращения SQL injection
                    if (isValidIdentifier(table)) {
                        String sql = String.format("VACUUM FULL %s", escapeIdentifier(table));
                        statement.execute(sql);
                    } else {
                        log.warn("Недопустимое имя таблицы: {}", table);
                    }
                } catch (Exception e) {
                    log.warn("Не удалось выполнить VACUUM FULL для таблицы {}: {}", table, e.getMessage());
                }
            }

            // Получаем размер после очистки
            long endSize = getDatabaseSizeViaJdbc(statement);

            long freedBytes = startSize - endSize;

            result.put("success", true);
            result.put("tablesProcessed", tables.size());
            result.put("initialSize", FileUtils.formatBytes(startSize));
            result.put("finalSize", FileUtils.formatBytes(endSize));
            result.put("freedSpace", FileUtils.formatBytes(freedBytes));
            result.put("freedSpaceBytes", freedBytes);

            log.info("VACUUM FULL завершен. Освобождено: {}", FileUtils.formatBytes(freedBytes));

        } catch (Exception e) {
            log.error("Ошибка при выполнении VACUUM FULL", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    public Map<String, Object> performReindex() {
        log.info("Запуск REINDEX для всех индексов");
        Map<String, Object> result = new HashMap<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            connection.setAutoCommit(true);

            // Получаем список всех индексов
            List<String[]> indexes = new ArrayList<>();
            try (ResultSet indexesRs = statement.executeQuery("""
                SELECT indexname, tablename
                FROM pg_indexes
                WHERE schemaname = 'public'
                AND indexname NOT LIKE '%_pkey'
                """)) {
                while (indexesRs.next()) {
                    indexes.add(new String[]{indexesRs.getString("indexname"), indexesRs.getString("tablename")});
                }
            }

            int processedCount = 0;
            for (String[] index : indexes) {
                String indexName = index[0];
                String tableName = index[1];
                log.info("REINDEX для индекса: {} (таблица: {})", indexName, tableName);

                try {
                    // Валидация имени индекса для предотвращения SQL injection
                    if (isValidIdentifier(indexName)) {
                        String sql = String.format("REINDEX INDEX %s", escapeIdentifier(indexName));
                        statement.execute(sql);
                        processedCount++;
                    } else {
                        log.warn("Недопустимое имя индекса: {}", indexName);
                    }
                } catch (Exception e) {
                    log.warn("Не удалось выполнить REINDEX для {}: {}", indexName, e.getMessage());
                }
            }

            result.put("success", true);
            result.put("totalIndexes", indexes.size());
            result.put("processedIndexes", processedCount);

            log.info("REINDEX завершен. Обработано индексов: {}/{}", processedCount, indexes.size());

        } catch (Exception e) {
            log.error("Ошибка при выполнении REINDEX", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    public List<Map<String, Object>> analyzeBloat() {
        log.info("Анализ раздувания (bloat) таблиц");
        List<Map<String, Object>> bloatInfo = new ArrayList<>();

        try {
            // Упрощенный анализ bloat через статистику таблиц
            String sql = """
                SELECT
                    t.tablename,
                    pg_size_pretty(pg_total_relation_size(t.schemaname||'.'||t.tablename)) as table_size,
                    pg_size_pretty(pg_relation_size(t.schemaname||'.'||t.tablename)) as relation_size,
                    CASE
                        WHEN pg_relation_size(t.schemaname||'.'||t.tablename) > 0
                        THEN ROUND(((pg_total_relation_size(t.schemaname||'.'||t.tablename) - pg_relation_size(t.schemaname||'.'||t.tablename))::numeric / pg_relation_size(t.schemaname||'.'||t.tablename) * 100), 2)
                        ELSE 0
                    END as index_bloat_pct,
                    CASE
                        WHEN s.n_dead_tup > 0 AND s.n_live_tup > 0
                        THEN ROUND((s.n_dead_tup::numeric / (s.n_live_tup + s.n_dead_tup) * 100), 2)
                        ELSE 0
                    END as dead_tuple_pct
                FROM pg_tables t
                LEFT JOIN pg_stat_user_tables s ON s.relname = t.tablename
                WHERE t.schemaname = 'public'
                AND pg_relation_size(t.schemaname||'.'||t.tablename) > 1048576
                ORDER BY pg_total_relation_size(t.schemaname||'.'||t.tablename) DESC
                LIMIT 10
                """;

            Query query = entityManager.createNativeQuery(sql);
            List<Object[]> results = query.getResultList();

            for (Object[] row : results) {
                Map<String, Object> bloat = new HashMap<>();
                bloat.put("tableName", row[0]);
                bloat.put("realSize", row[1]);
                bloat.put("expectedSize", row[2]);
                bloat.put("bloatPercent", row[3]);
                bloat.put("wastedSpace", row[4] + "% dead tuples");
                bloatInfo.add(bloat);
            }

            log.info("Анализ bloat завершен. Найдено {} таблиц для анализа", bloatInfo.size());

        } catch (Exception e) {
            log.error("Ошибка при анализе bloat", e);
        }

        return bloatInfo;
    }

    /**
     * Проверяет наличие расширения pg_stat_statements в PostgreSQL
     */
    private boolean isPgStatStatementsAvailable() {
        if (pgStatStatementsAvailable != null) {
            return pgStatStatementsAvailable;
        }

        try {
            String sql = "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements')";
            Query query = entityManager.createNativeQuery(sql);
            pgStatStatementsAvailable = (Boolean) query.getSingleResult();

            if (!pgStatStatementsAvailable) {
                log.info("pg_stat_statements не установлен. Используется альтернативный анализ через pg_stat_activity.");
                log.info("Для расширенной статистики запросов можно установить: CREATE EXTENSION pg_stat_statements;");
            }

            return pgStatStatementsAvailable;
        } catch (Exception e) {
            log.debug("Ошибка проверки pg_stat_statements: {}", e.getMessage());
            pgStatStatementsAvailable = false;
            return false;
        }
    }

    public List<QueryPerformanceDto> analyzeQueryPerformance() {
        log.info("Запуск анализа производительности запросов");

        // Проверяем наличие pg_stat_statements
        if (!isPgStatStatementsAvailable()) {
            log.debug("pg_stat_statements недоступен, используем pg_stat_activity");
            return analyzeActiveQueries();
        }

        try {
            String sql = """
                SELECT
                    query,
                    calls,
                    total_exec_time / calls as avg_time,
                    max_exec_time,
                    stddev_exec_time,
                    (100.0 * shared_blks_hit / nullif(shared_blks_hit + shared_blks_read, 0)) AS hit_percent
                FROM pg_stat_statements
                WHERE calls > 5
                ORDER BY avg_time DESC
                LIMIT 20
                """;

            Query query = entityManager.createNativeQuery(sql);
            List<Object[]> results = query.getResultList();

            log.info("Анализ через pg_stat_statements завершен. Найдено {} запросов", results.size());
            return results.stream().map(this::mapToQueryPerformanceDto).collect(Collectors.toList());

        } catch (Exception e) {
            log.info("Ошибка при использовании pg_stat_statements, переключаюсь на pg_stat_activity: {}", e.getMessage());
            pgStatStatementsAvailable = false; // Обновляем кэш
            return analyzeActiveQueries();
        }
    }

    /**
     * Анализ активных запросов через pg_stat_activity (альтернатива pg_stat_statements)
     */
    public List<QueryPerformanceDto> analyzeActiveQueries() {
        log.info("Запуск анализа активных запросов через pg_stat_activity");

        try {
            String sql = """
                SELECT
                    pid,
                    usename,
                    application_name,
                    client_addr::text,
                    datname,
                    state,
                    query,
                    EXTRACT(EPOCH FROM (now() - query_start)) * 1000 as duration_ms,
                    wait_event_type,
                    wait_event,
                    CASE
                        WHEN state = 'active' AND query NOT LIKE 'autovacuum:%' THEN 'ACTIVE'
                        WHEN state = 'idle in transaction' THEN 'IDLE_IN_TRANSACTION'
                        WHEN state = 'idle' THEN 'IDLE'
                        ELSE 'OTHER'
                    END as query_state
                FROM pg_stat_activity
                WHERE
                    datname = current_database()
                    AND pid != pg_backend_pid()
                    AND query NOT LIKE '%pg_stat_activity%'
                    AND state != 'idle'
                ORDER BY duration_ms DESC NULLS LAST
                LIMIT 20
                """;

            Query query = entityManager.createNativeQuery(sql);
            List<Object[]> results = query.getResultList();

            List<QueryPerformanceDto> performanceList = results.stream()
                .map(this::mapActiveQueryToPerformanceDto)
                .collect(Collectors.toList());

            // Добавляем статистику по таблицам
            performanceList.addAll(analyzeTablePerformance());

            log.info("Анализ активных запросов завершен. Найдено {} элементов", performanceList.size());

            return performanceList;

        } catch (Exception e) {
            log.error("Ошибка при анализе активных запросов", e);
            return Collections.emptyList();
        }
    }

    /**
     * Анализ производительности таблиц через pg_stat_user_tables
     */
    private List<QueryPerformanceDto> analyzeTablePerformance() {
        log.info("Анализ производительности таблиц");

        try {
            String sql = """
                SELECT
                    schemaname || '.' || relname as table_name,
                    seq_scan,
                    seq_tup_read,
                    idx_scan,
                    idx_tup_fetch,
                    n_tup_ins + n_tup_upd + n_tup_del as total_modifications,
                    n_live_tup,
                    n_dead_tup,
                    CASE
                        WHEN n_live_tup > 0 THEN ROUND((n_dead_tup::numeric / n_live_tup * 100), 2)
                        ELSE 0
                    END as dead_tuple_percent
                FROM pg_stat_user_tables
                WHERE schemaname = 'public'
                AND (seq_scan > 100 OR n_dead_tup > 1000)
                ORDER BY seq_scan DESC, n_dead_tup DESC
                LIMIT 10
                """;

            Query query = entityManager.createNativeQuery(sql);
            List<Object[]> results = query.getResultList();

            return results.stream()
                .map(this::mapTableStatsToPerformanceDto)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Ошибка при анализе производительности таблиц: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public DatabaseStatsDto getDatabaseStats() {
        log.info("Сбор статистики базы данных");
        
        DatabaseStatsDto stats = new DatabaseStatsDto();
        
        try {
            stats.setTotalSizeBytes(getDatabaseSize());
            stats.setFormattedTotalSize(FileUtils.formatBytes(stats.getTotalSizeBytes()));
            stats.setTableSizes(getTableSizes());
            stats.setFormattedTableSizes(formatTableSizes(stats.getTableSizes()));
            stats.setTotalTables(getTotalTables());
            stats.setTotalIndexes(getTotalIndexes());
            stats.setActiveConnections(getActiveConnections());
            stats.setCacheHitRatio(getCacheHitRatio());
            stats.setLastVacuum(getLastVacuumTime());
            stats.setLastAnalyze(getLastAnalyzeTime());
            
            log.info("Статистика БД собрана: размер={}, таблиц={}, индексов={}", 
                stats.getFormattedTotalSize(), stats.getTotalTables(), stats.getTotalIndexes());
            
        } catch (Exception e) {
            log.error("Ошибка при сборе статистики БД", e);
        }
        
        return stats;
    }


    private QueryPerformanceDto mapToQueryPerformanceDto(Object[] row) {
        QueryPerformanceDto dto = new QueryPerformanceDto();
        dto.setQuery(truncateQuery((String) row[0]));
        dto.setQueryHash(Integer.toHexString(dto.getQuery().hashCode()));
        dto.setCallCount(((Number) row[1]).intValue());
        dto.setAvgExecutionTimeMs(((Number) row[2]).longValue());
        dto.setMaxExecutionTimeMs(((Number) row[3]).longValue());
        dto.setSlowQuery(dto.getAvgExecutionTimeMs() > slowQueryThresholdMs);
        dto.setCpuUsagePercent(((Number) row[5]).doubleValue());

        if (dto.isSlowQuery()) {
            dto.setRecommendation("Рассмотрите оптимизацию запроса или добавление индексов");
        } else {
            dto.setRecommendation("Производительность в норме");
        }

        return dto;
    }

    /**
     * Маппинг активных запросов из pg_stat_activity в QueryPerformanceDto
     */
    private QueryPerformanceDto mapActiveQueryToPerformanceDto(Object[] row) {
        QueryPerformanceDto dto = new QueryPerformanceDto();

        // row[0] = pid, row[1] = usename, row[2] = application_name, row[3] = client_addr
        // row[4] = datname, row[5] = state, row[6] = query, row[7] = duration_ms
        // row[8] = wait_event_type, row[9] = wait_event, row[10] = query_state

        dto.setPid(((Number) row[0]).longValue());
        dto.setUserName((String) row[1]);
        dto.setApplicationName((String) row[2]);
        dto.setClientAddr((String) row[3]);
        dto.setDatabase((String) row[4]);
        dto.setState((String) row[5]);
        dto.setQuery(truncateQuery((String) row[6]));
        dto.setQueryHash(Integer.toHexString(dto.getQuery().hashCode()));

        // Длительность выполнения
        Object durationObj = row[7];
        if (durationObj != null) {
            dto.setQueryDurationMs(((Number) durationObj).longValue());
            dto.setAvgExecutionTimeMs(dto.getQueryDurationMs());
        } else {
            dto.setQueryDurationMs(0L);
            dto.setAvgExecutionTimeMs(0L);
        }

        dto.setWaitEventType((String) row[8]);
        String queryState = (String) row[10];

        // Определяем тип запроса
        String query = (String) row[6];
        if (query != null) {
            String queryUpper = query.trim().toUpperCase();
            if (queryUpper.startsWith("SELECT")) {
                dto.setQueryType("SELECT");
            } else if (queryUpper.startsWith("INSERT")) {
                dto.setQueryType("INSERT");
            } else if (queryUpper.startsWith("UPDATE")) {
                dto.setQueryType("UPDATE");
            } else if (queryUpper.startsWith("DELETE")) {
                dto.setQueryType("DELETE");
            } else {
                dto.setQueryType("OTHER");
            }
        }

        // Определяем severity
        if ("IDLE_IN_TRANSACTION".equals(queryState)) {
            dto.setSeverity("WARNING");
            dto.setRecommendation("Транзакция простаивает. Рекомендуется завершить транзакцию.");
        } else if (dto.getQueryDurationMs() != null && dto.getQueryDurationMs() > slowQueryThresholdMs) {
            dto.setSeverity("CRITICAL");
            dto.setSlowQuery(true);
            dto.setRecommendation("Запрос выполняется слишком долго (" + dto.getQueryDurationMs() + " мс). Проверьте оптимизацию.");
        } else if (dto.getWaitEventType() != null) {
            dto.setSeverity("WARNING");
            dto.setRecommendation("Запрос ожидает события: " + dto.getWaitEventType());
        } else {
            dto.setSeverity("INFO");
            dto.setRecommendation("Запрос выполняется нормально");
        }

        dto.setCallCount(1); // Для активных запросов это всегда 1

        return dto;
    }

    /**
     * Маппинг статистики таблиц из pg_stat_user_tables в QueryPerformanceDto
     *
     * Улучшенная логика с учётом размера таблицы и понятными рекомендациями:
     * - Для маленьких таблиц (< 100 записей) seq_scan это норма
     * - Для средних таблиц (100-1000) учитывается частота сканирований
     * - Для больших таблиц (> 1000) seq_scan критичен для производительности
     * - Мёртвые кортежи анализируются с учётом абсолютного количества и процента
     */
    private QueryPerformanceDto mapTableStatsToPerformanceDto(Object[] row) {
        QueryPerformanceDto dto = new QueryPerformanceDto();

        // row[0] = table_name, row[1] = seq_scan, row[2] = seq_tup_read
        // row[3] = idx_scan, row[4] = idx_tup_fetch, row[5] = total_modifications
        // row[6] = n_live_tup, row[7] = n_dead_tup, row[8] = dead_tuple_percent

        String tableName = (String) row[0];
        long seqScan = ((Number) row[1]).longValue();
        long idxScan = row[3] != null ? ((Number) row[3]).longValue() : 0L;
        long liveTuples = ((Number) row[6]).longValue();
        long deadTuples = ((Number) row[7]).longValue();
        double deadTuplePercent = ((Number) row[8]).doubleValue();

        dto.setTableName(tableName);
        dto.setQuery("TABLE STATS: " + tableName);
        dto.setQueryHash(Integer.toHexString(tableName.hashCode()));
        dto.setQueryType("TABLE_STATS");
        dto.setCallCount((int) (seqScan + idxScan));
        dto.setRowsReturned(liveTuples);

        // Улучшенная логика определения severity с учётом размера таблицы
        String severity = "INFO";
        String recommendation;
        boolean isSlowQuery = false;

        // ПРИОРИТЕТ 1: Критическая проблема с мёртвыми кортежами (bloat)
        if (deadTuplePercent > 30 && deadTuples > 10000) {
            severity = "CRITICAL";
            recommendation = String.format(
                "⚠️ КРИТИЧЕСКИЙ BLOAT: %.1f%% мёртвых кортежей (%,d шт из %,d). " +
                "Таблица сильно раздута и замедляет работу БД. " +
                "📋 ДЕЙСТВИЯ: 1) Выполните VACUUM ANALYZE (кнопка слева). " +
                "2) Нажмите 'Обновить статистику' для проверки. " +
                "3) Если bloat > 50%%, рассмотрите VACUUM FULL (блокирует таблицу!).",
                deadTuplePercent, deadTuples, liveTuples + deadTuples
            );
            isSlowQuery = true;
        }
        // ПРИОРИТЕТ 2: Высокий процент мёртвых кортежей
        else if (deadTuplePercent > 20 && deadTuples > 1000) {
            severity = "WARNING";
            recommendation = String.format(
                "⚠️ ВЫСОКИЙ BLOAT: %.1f%% мёртвых кортежей (%,d шт из %,d). " +
                "Таблица начинает раздуваться и занимает лишнее место. " +
                "📋 ДЕЙСТВИЯ: Выполните VACUUM ANALYZE для очистки, затем нажмите 'Обновить статистику'.",
                deadTuplePercent, deadTuples, liveTuples + deadTuples
            );
        }
        // ПРИОРИТЕТ 3: Проблема с индексами для БОЛЬШИХ таблиц
        else if (liveTuples >= 1000 && seqScan > 500 && (idxScan == 0 || seqScan > idxScan * 10)) {
            severity = "CRITICAL";
            recommendation = String.format(
                "🔍 МЕДЛЕННЫЕ ЗАПРОСЫ: Большая таблица (%,d записей) сканируется последовательно " +
                "(%,d seq_scan vs %,d index_scan). При таком объёме это ОЧЕНЬ медленно! " +
                "📋 ДЕЙСТВИЯ: 1) Проанализируйте медленные запросы через логи или pg_stat_activity. " +
                "2) Добавьте индексы на поля в WHERE/JOIN/ORDER BY. " +
                "3) Используйте EXPLAIN ANALYZE для проверки эффективности.",
                liveTuples, seqScan, idxScan
            );
            isSlowQuery = true;
        }
        // ПРИОРИТЕТ 4: Средняя таблица с частыми seq_scan
        else if (liveTuples >= 100 && liveTuples < 1000 && seqScan > 1000 && idxScan < seqScan) {
            severity = "WARNING";
            recommendation = String.format(
                "🔍 ЧАСТЫЕ СКАНИРОВАНИЯ: Таблица (%,d записей) часто сканируется полностью (%,d раз). " +
                "Для среднего размера это приемлемо, но можно ускорить. " +
                "📋 РЕКОМЕНДАЦИЯ: Проанализируйте частые запросы с помощью EXPLAIN ANALYZE. " +
                "Возможно, нужны индексы.",
                liveTuples, seqScan
            );
        }
        // ПРИОРИТЕТ 5: Маленькая таблица - seq_scan это НОРМА
        else if (liveTuples < 100 && seqScan > 0) {
            severity = "INFO";
            recommendation = String.format(
                "✅ НОРМА: Маленькая таблица (%,d %s). " +
                "Последовательное сканирование (%,d раз) - это НОРМАЛЬНО и даже быстрее индексов! " +
                "PostgreSQL правильно выбирает стратегию. Оптимизация НЕ требуется.",
                liveTuples, liveTuples == 1 ? "запись" : liveTuples < 5 ? "записи" : "записей", seqScan
            );
        }
        // ПРИОРИТЕТ 6: Умеренные мёртвые кортежи
        else if (deadTuplePercent > 10 && deadTuples > 100) {
            severity = "INFO";
            recommendation = String.format(
                "ℹ️ НЕБОЛЬШОЙ BLOAT: %.1f%% мёртвых кортежей (%,d шт). " +
                "Уровень приемлемый, но можно улучшить для экономии места. " +
                "📋 РЕКОМЕНДАЦИЯ: Настройте autovacuum или периодически запускайте VACUUM вручную.",
                deadTuplePercent, deadTuples
            );
        }
        // Всё в норме
        else {
            severity = "INFO";
            String scanInfo = seqScan + idxScan > 0 ?
                String.format(", %,d сканирований (seq: %,d, idx: %,d)", seqScan + idxScan, seqScan, idxScan) :
                "";
            recommendation = String.format(
                "✅ ОПТИМАЛЬНО: Таблица работает отлично (%,d записей, %.1f%% dead tuples%s). " +
                "Дополнительная оптимизация не требуется.",
                liveTuples, deadTuplePercent, scanInfo
            );
        }

        dto.setSeverity(severity);
        dto.setRecommendation(recommendation);
        dto.setSlowQuery(isSlowQuery);

        return dto;
    }

    private long getDatabaseSize() {
        try {
            String sql = "SELECT pg_database_size(current_database())";
            Query query = entityManager.createNativeQuery(sql);
            return ((Number) query.getSingleResult()).longValue();
        } catch (Exception e) {
            log.warn("Не удалось получить размер БД: {}", e.getMessage());
            return 0;
        }
    }

    private Map<String, Long> getTableSizes() {
        Map<String, Long> tableSizes = new HashMap<>();
        
        try {
            String sql = """
                SELECT 
                    schemaname,
                    tablename,
                    pg_total_relation_size(schemaname||'.'||tablename) as size
                FROM pg_tables 
                WHERE schemaname = 'public'
                ORDER BY size DESC
                LIMIT 10
                """;
            
            Query query = entityManager.createNativeQuery(sql);
            List<Object[]> results = query.getResultList();
            
            for (Object[] row : results) {
                String tableName = (String) row[1];
                Long size = ((Number) row[2]).longValue();
                tableSizes.put(tableName, size);
            }
            
        } catch (Exception e) {
            log.warn("Не удалось получить размеры таблиц: {}", e.getMessage());
        }
        
        return tableSizes;
    }

    private Map<String, String> formatTableSizes(Map<String, Long> tableSizes) {
        return tableSizes.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> FileUtils.formatBytes(entry.getValue())
            ));
    }

    private int getTotalTables() {
        try {
            String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'";
            Query query = entityManager.createNativeQuery(sql);
            return ((Number) query.getSingleResult()).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private int getTotalIndexes() {
        try {
            String sql = "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public'";
            Query query = entityManager.createNativeQuery(sql);
            return ((Number) query.getSingleResult()).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private long getActiveConnections() {
        try {
            String sql = "SELECT COUNT(*) FROM pg_stat_activity WHERE state = 'active'";
            Query query = entityManager.createNativeQuery(sql);
            return ((Number) query.getSingleResult()).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private double getCacheHitRatio() {
        try {
            String sql = """
                SELECT 
                    round((blks_hit::float / (blks_hit + blks_read) * 100)::numeric, 2) 
                FROM pg_stat_database 
                WHERE datname = current_database()
                """;
            Query query = entityManager.createNativeQuery(sql);
            return ((Number) query.getSingleResult()).doubleValue();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private LocalDateTime getLastVacuumTime() {
        try {
            String sql = """
                SELECT MAX(GREATEST(
                    COALESCE(last_vacuum, '1970-01-01'::timestamp),
                    COALESCE(last_autovacuum, '1970-01-01'::timestamp)
                ))
                FROM pg_stat_user_tables
                WHERE GREATEST(
                    COALESCE(last_vacuum, '1970-01-01'::timestamp),
                    COALESCE(last_autovacuum, '1970-01-01'::timestamp)
                ) > '1970-01-01'::timestamp
                """;
            Query query = entityManager.createNativeQuery(sql);
            Object result = query.getSingleResult();

            if (result == null) {
                return null;
            }

            // PostgreSQL может вернуть Timestamp или Instant в зависимости от JDBC драйвера
            if (result instanceof Timestamp) {
                return ((Timestamp) result).toLocalDateTime();
            } else if (result instanceof java.time.Instant) {
                return LocalDateTime.ofInstant((java.time.Instant) result, java.time.ZoneId.systemDefault());
            } else {
                log.warn("Неожиданный тип результата для last_vacuum: {}", result.getClass().getName());
                return null;
            }
        } catch (Exception e) {
            log.warn("Не удалось получить время последнего VACUUM: {}", e.getMessage());
            return null;
        }
    }

    private LocalDateTime getLastAnalyzeTime() {
        try {
            String sql = "SELECT MAX(last_analyze) FROM pg_stat_user_tables";
            Query query = entityManager.createNativeQuery(sql);
            Object result = query.getSingleResult();

            if (result == null) {
                return null;
            }

            // PostgreSQL может вернуть Timestamp или Instant в зависимости от JDBC драйвера
            if (result instanceof Timestamp) {
                return ((Timestamp) result).toLocalDateTime();
            } else if (result instanceof java.time.Instant) {
                return LocalDateTime.ofInstant((java.time.Instant) result, java.time.ZoneId.systemDefault());
            } else {
                log.warn("Неожиданный тип результата для last_analyze: {}", result.getClass().getName());
                return null;
            }
        } catch (Exception e) {
            log.warn("Не удалось получить время последнего ANALYZE: {}", e.getMessage());
            return null;
        }
    }


    private String truncateQuery(String query) {
        if (query == null) return "";
        return query.length() > 100 ? query.substring(0, 100) + "..." : query;
    }

    /**
     * Валидация SQL идентификатора для предотвращения SQL injection
     */
    private boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }
        // Проверяем, что идентификатор содержит только допустимые символы
        // PostgreSQL: буквы, цифры, подчеркивания, начинается с буквы или подчеркивания
        return identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$") && identifier.length() <= 63;
    }

    /**
     * Экранирование SQL идентификатора
     */
    private String escapeIdentifier(String identifier) {
        // В PostgreSQL идентификаторы экранируются двойными кавычками
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private long getDatabaseSizeViaJdbc(Statement statement) {
        try (ResultSet rs = statement.executeQuery("SELECT pg_database_size(current_database())")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Не удалось получить размер БД через JDBC: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Принудительно обновляет статистику PostgreSQL через ANALYZE
     * Это заставляет PostgreSQL пересчитать n_live_tup и n_dead_tup для всех таблиц
     */
    public Map<String, Object> refreshTableStatistics() {
        log.info("Запуск принудительного обновления статистики таблиц (ANALYZE)");
        Map<String, Object> result = new HashMap<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            connection.setAutoCommit(true);

            // Получаем список всех таблиц
            List<String> tables = new ArrayList<>();
            try (ResultSet tablesRs = statement.executeQuery(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'")) {
                while (tablesRs.next()) {
                    tables.add(tablesRs.getString("tablename"));
                }
            }

            // ANALYZE для каждой таблицы
            int processedCount = 0;
            for (String table : tables) {
                log.info("ANALYZE для таблицы: {}", table);
                try {
                    if (isValidIdentifier(table)) {
                        String sql = String.format("ANALYZE %s", escapeIdentifier(table));
                        statement.execute(sql);
                        processedCount++;
                    } else {
                        log.warn("Недопустимое имя таблицы: {}", table);
                    }
                } catch (Exception e) {
                    log.warn("Не удалось выполнить ANALYZE для таблицы {}: {}", table, e.getMessage());
                }
            }

            result.put("success", true);
            result.put("totalTables", tables.size());
            result.put("processedTables", processedCount);
            result.put("message", "Статистика обновлена для " + processedCount + " таблиц");

            log.info("ANALYZE завершен. Обработано таблиц: {}/{}", processedCount, tables.size());

        } catch (Exception e) {
            log.error("Ошибка при обновлении статистики таблиц", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }
}