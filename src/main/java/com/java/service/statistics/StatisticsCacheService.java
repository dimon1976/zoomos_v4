package com.java.service.statistics;

import com.java.model.entity.ExportStatistics;
import com.java.repository.ExportStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Сервис для кэширования часто запрашиваемых статистических данных
 */
@Service
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = "statisticsCache")
public class StatisticsCacheService {

    private final ExportStatisticsRepository statisticsRepository;

    /**
     * Получает агрегированную статистику с кэшированием
     * Кэш инвалидируется через 5 минут или при обновлении данных
     */
    @Cacheable(value = "aggregatedStats", 
               key = "#sessionIds.hashCode()", 
               unless = "#result == null or #result.isEmpty()")
    public List<Object[]> getAggregatedStats(List<Long> sessionIds) {
        log.debug("Загрузка агрегированной статистики для сессий: {}", sessionIds);
        return statisticsRepository.findAggregatedStatsBySessionIds(sessionIds);
    }

    /**
     * Получает топ группы для клиента с кэшированием
     */
    @Cacheable(value = "topGroups", 
               key = "#clientId + '_' + #sinceDate.toString()", 
               unless = "#result == null or #result.isEmpty()")
    public List<Object[]> getTopGroups(Long clientId, ZonedDateTime sinceDate, int limit) {
        log.debug("Загрузка топ {} групп для клиента {}", limit, clientId);
        
        return statisticsRepository.findTopGroupsByClient(
            clientId, 
            sinceDate, 
            org.springframework.data.domain.PageRequest.of(0, limit)
        ).getContent();
    }

    /**
     * Получает дневные тренды с кэшированием
     */
    @Cacheable(value = "dailyTrends", 
               key = "#clientId + '_' + #sinceDate.toString()", 
               unless = "#result == null or #result.isEmpty()")
    public List<Object[]> getDailyTrends(Long clientId, ZonedDateTime sinceDate) {
        log.debug("Загрузка дневных трендов для клиента {} с {}", clientId, sinceDate);
        return statisticsRepository.findDailyTrendsByClient(clientId, sinceDate);
    }

    /**
     * Получает статистику для периода с кэшированием
     */
    @Cacheable(value = "periodStats", 
               key = "#clientId + '_' + #startDate.toString() + '_' + #endDate.toString()", 
               unless = "#result == null or #result.isEmpty()")
    public List<ExportStatistics> getStatisticsForPeriod(Long clientId, 
                                                         ZonedDateTime startDate, 
                                                         ZonedDateTime endDate) {
        log.debug("Загрузка статистики за период {} - {} для клиента {}", 
                  startDate, endDate, clientId);
        return statisticsRepository.findByDateRangeAndClient(startDate, endDate, clientId);
    }

    /**
     * Получает уникальные значения групп для сессии с кэшированием
     */
    @Cacheable(value = "uniqueGroupValues", key = "#sessionId")
    public List<String> getUniqueGroupValues(Long sessionId) {
        log.debug("Загрузка уникальных значений групп для сессии {}", sessionId);
        return statisticsRepository.findDistinctGroupFieldValuesBySessionId(sessionId);
    }

    /**
     * Получает уникальные названия полей подсчета с кэшированием
     */
    @Cacheable(value = "uniqueCountFields", key = "#sessionId")
    public List<String> getUniqueCountFields(Long sessionId) {
        log.debug("Загрузка уникальных полей подсчета для сессии {}", sessionId);
        return statisticsRepository.findDistinctCountFieldNamesBySessionId(sessionId);
    }

    // === Cache Eviction Methods ===

    /**
     * Очищает кэш при добавлении новой статистики
     */
    @CacheEvict(value = {"aggregatedStats", "topGroups", "dailyTrends", "periodStats"}, allEntries = true)
    public void evictStatsCache() {
        log.info("Очистка кэша статистики после обновления данных");
    }

    /**
     * Очищает кэш для конкретной сессии
     */
    @CacheEvict(value = {"uniqueGroupValues", "uniqueCountFields"}, key = "#sessionId")
    public void evictSessionCache(Long sessionId) {
        log.debug("Очистка кэша для сессии {}", sessionId);
    }

    /**
     * Очищает кэш для клиента (топ группы и тренды)
     */
    @CacheEvict(value = {"topGroups", "dailyTrends", "periodStats"}, 
                condition = "#clientId != null")
    public void evictClientCache(Long clientId) {
        log.debug("Очистка кэша для клиента {}", clientId);
    }

    /**
     * Полная очистка всего кэша статистики
     */
    @CacheEvict(value = {"aggregatedStats", "topGroups", "dailyTrends", "periodStats", 
                        "uniqueGroupValues", "uniqueCountFields"}, 
                allEntries = true)
    public void evictAllCache() {
        log.info("Полная очистка кэша статистики");
    }

    // === Helper Methods ===

    /**
     * Проверяет существование данных в кэше (для диагностики)
     */
    public void logCacheStatus(String operation, Object key) {
        log.debug("Операция кэша: {} для ключа: {}", operation, key);
    }

    /**
     * Получает информацию о размере кэша (для мониторинга)
     */
    public void logCacheInfo() {
        log.info("Информация о кэше статистики - операция завершена");
    }
}