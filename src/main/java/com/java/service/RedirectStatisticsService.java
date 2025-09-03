package com.java.service;

import com.java.model.entity.RedirectStatistics;
import com.java.repository.RedirectStatisticsRepository;
import com.java.service.utils.RedirectCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Сервис для сбора и анализа статистики редиректов
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedirectStatisticsService {

    private final RedirectStatisticsRepository redirectStatisticsRepository;

    /**
     * Сохранить результат обработки URL
     */
    @Transactional
    public void saveRedirectResult(String originalUrl, String finalUrl, String strategyName, 
                                   RedirectCollectorService.RedirectResult result, long processingTimeMs) {
        try {
            RedirectStatistics stats = RedirectStatistics.fromResult(
                originalUrl, strategyName, result.getStatus(), result.getRedirectCount(), processingTimeMs
            );
            
            // Дополняем данными из result
            stats.setFinalUrl(finalUrl != null ? finalUrl : result.getFinalUrl());
            stats.setRetryCount(0); // пока без retry логики
            
            // Извлекаем HTTP статус код из статуса если возможно
            extractHttpStatusCode(stats, result.getStatus());
            
            redirectStatisticsRepository.save(stats);
            
            log.debug("Сохранена статистика: URL={}, strategy={}, status={}, time={}ms", 
                    originalUrl, strategyName, result.getStatus(), processingTimeMs);
                    
        } catch (Exception e) {
            log.warn("Ошибка сохранения статистики для URL {}: {}", originalUrl, e.getMessage());
        }
    }

    private void extractHttpStatusCode(RedirectStatistics stats, String status) {
        try {
            if (status.startsWith("HTTP_")) {
                String statusCode = status.substring(5);
                stats.setHttpStatusCode(Integer.parseInt(statusCode));
            } else if (status.contains("403")) {
                stats.setHttpStatusCode(403);
            } else if (status.contains("429")) {
                stats.setHttpStatusCode(429);
            } else if (status.contains("404")) {
                stats.setHttpStatusCode(404);
            }
        } catch (Exception e) {
            // Игнорируем ошибки парсинга статус кода
        }
    }

    /**
     * Получить статистику эффективности стратегий за последние N дней
     */
    public List<Map<String, Object>> getStrategyEffectiveness(int days) {
        ZonedDateTime fromDate = ZonedDateTime.now().minusDays(days);
        ZonedDateTime toDate = ZonedDateTime.now();
        
        return redirectStatisticsRepository.getStrategySuccessRates(fromDate, toDate);
    }

    /**
     * Получить топ проблемных доменов
     */
    public List<Map<String, Object>> getTopBlockedDomains(int days, int minAttempts, int limit) {
        ZonedDateTime fromDate = ZonedDateTime.now().minusDays(days);
        ZonedDateTime toDate = ZonedDateTime.now();
        
        return redirectStatisticsRepository.getTopBlockedDomains(fromDate, toDate, minAttempts, limit);
    }

    /**
     * Получить общую статистику за период
     */
    public Map<String, Object> getOverallStatistics(int days) {
        ZonedDateTime fromDate = ZonedDateTime.now().minusDays(days);
        ZonedDateTime toDate = ZonedDateTime.now();
        
        return redirectStatisticsRepository.getOverallStatistics(fromDate, toDate);
    }

    /**
     * Получить распределение статусов
     */
    public List<Map<String, Object>> getStatusDistribution(int days) {
        ZonedDateTime fromDate = ZonedDateTime.now().minusDays(days);
        ZonedDateTime toDate = ZonedDateTime.now();
        
        return redirectStatisticsRepository.getStatusDistribution(fromDate, toDate);
    }

    /**
     * Получить динамику успешности по часам
     */
    public List<Map<String, Object>> getHourlySuccessRate(int days) {
        ZonedDateTime fromDate = ZonedDateTime.now().minusDays(days);
        ZonedDateTime toDate = ZonedDateTime.now();
        
        return redirectStatisticsRepository.getHourlySuccessRate(fromDate, toDate);
    }

    /**
     * Получить рекомендуемую стратегию для домена
     */
    public String getRecommendedStrategyForDomain(String domain) {
        ZonedDateTime fromDate = ZonedDateTime.now().minusDays(30); // за последний месяц
        
        List<Map<String, Object>> strategies = redirectStatisticsRepository
                .getBestStrategiesForDomain(domain, fromDate);
        
        if (!strategies.isEmpty()) {
            Map<String, Object> bestStrategy = strategies.get(0);
            return (String) bestStrategy.get("strategy_name");
        }
        
        return null; // нет данных для рекомендации
    }

    /**
     * Получить последние неудачи для анализа
     */
    public List<RedirectStatistics> getRecentFailures(int hours) {
        ZonedDateTime fromDate = ZonedDateTime.now().minusHours(hours);
        return redirectStatisticsRepository.getRecentFailures(fromDate);
    }

    /**
     * Поиск статистики по URL или домену
     */
    public List<RedirectStatistics> findByUrlOrDomain(String query) {
        return redirectStatisticsRepository.findByUrlOrDomain(query);
    }

    /**
     * Получить сегодняшнюю статистику по стратегиям
     */
    public List<Map<String, Object>> getTodayStatistics() {
        return redirectStatisticsRepository.getSuccessfulRequestsToday();
    }

    /**
     * Очистка старых записей статистики (для maintenance)
     */
    @Transactional
    public int cleanupOldStatistics(int daysToKeep) {
        ZonedDateTime beforeDate = ZonedDateTime.now().minusDays(daysToKeep);
        
        long countBefore = redirectStatisticsRepository.count();
        redirectStatisticsRepository.deleteByCreatedAtBefore(beforeDate);
        long countAfter = redirectStatisticsRepository.count();
        
        int deletedCount = (int) (countBefore - countAfter);
        
        log.info("Очистка статистики редиректов: удалено {} записей старше {} дней", 
                deletedCount, daysToKeep);
        
        return deletedCount;
    }

    /**
     * Проверка работоспособности системы статистики
     */
    public boolean isHealthy() {
        try {
            // Простая проверка - можем ли мы получить count записей
            long count = redirectStatisticsRepository.count();
            log.debug("RedirectStatisticsService healthy: {} records in database", count);
            return true;
        } catch (Exception e) {
            log.warn("RedirectStatisticsService health check failed: {}", e.getMessage());
            return false;
        }
    }
}