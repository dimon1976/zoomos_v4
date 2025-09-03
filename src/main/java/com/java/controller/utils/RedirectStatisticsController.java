package com.java.controller.utils;

import com.java.model.entity.RedirectStatistics;
import com.java.service.RedirectStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Контроллер для просмотра статистики редиректов
 */
@Controller
@RequestMapping("/utils/redirect-statistics")
@RequiredArgsConstructor
@Slf4j
public class RedirectStatisticsController {

    private final RedirectStatisticsService statisticsService;

    /**
     * Главная страница статистики
     */
    @GetMapping
    public String statisticsPage(
            @RequestParam(defaultValue = "7") int days,
            Model model) {
        
        log.debug("GET request to redirect statistics page with days: {}", days);
        
        try {
            // Общая статистика
            Map<String, Object> overallStats = statisticsService.getOverallStatistics(days);
            model.addAttribute("overallStats", overallStats);
            
            // Статистика по стратегиям
            List<Map<String, Object>> strategyStats = statisticsService.getStrategyEffectiveness(days);
            model.addAttribute("strategyStats", strategyStats);
            
            // Топ проблемных доменов
            List<Map<String, Object>> blockedDomains = statisticsService.getTopBlockedDomains(days, 2, 10);
            model.addAttribute("blockedDomains", blockedDomains);
            
            // Распределение статусов
            List<Map<String, Object>> statusDistribution = statisticsService.getStatusDistribution(days);
            model.addAttribute("statusDistribution", statusDistribution);
            
            // Сегодняшняя статистика
            List<Map<String, Object>> todayStats = statisticsService.getTodayStatistics();
            model.addAttribute("todayStats", todayStats);
            
            model.addAttribute("selectedDays", days);
            
        } catch (Exception e) {
            log.error("Ошибка получения статистики: {}", e.getMessage());
            model.addAttribute("error", "Ошибка получения статистики: " + e.getMessage());
        }
        
        return "utils/redirect-statistics";
    }

    /**
     * API endpoint для получения динамики по часам
     */
    @GetMapping("/api/hourly")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getHourlyStats(
            @RequestParam(defaultValue = "7") int days) {
        
        try {
            List<Map<String, Object>> hourlyStats = statisticsService.getHourlySuccessRate(days);
            return ResponseEntity.ok(hourlyStats);
        } catch (Exception e) {
            log.error("Ошибка получения почасовой статистики: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Поиск статистики по URL или домену
     */
    @GetMapping("/search")
    @ResponseBody 
    public ResponseEntity<List<RedirectStatistics>> searchStatistics(
            @RequestParam String query) {
        
        log.debug("Search request for query: {}", query);
        
        try {
            List<RedirectStatistics> results = statisticsService.findByUrlOrDomain(query);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Ошибка поиска статистики: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получить рекомендуемую стратегию для домена
     */
    @GetMapping("/api/recommend")
    @ResponseBody
    public ResponseEntity<Map<String, String>> getRecommendedStrategy(
            @RequestParam String domain) {
        
        log.debug("Strategy recommendation request for domain: {}", domain);
        
        try {
            String recommendedStrategy = statisticsService.getRecommendedStrategyForDomain(domain);
            
            if (recommendedStrategy != null) {
                return ResponseEntity.ok(Map.of(
                    "domain", domain,
                    "recommendedStrategy", recommendedStrategy
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "domain", domain,
                    "message", "Недостаточно данных для рекомендации"
                ));
            }
        } catch (Exception e) {
            log.error("Ошибка получения рекомендации стратегии: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получить последние неудачи
     */
    @GetMapping("/api/failures")
    @ResponseBody
    public ResponseEntity<List<RedirectStatistics>> getRecentFailures(
            @RequestParam(defaultValue = "24") int hours) {
        
        try {
            List<RedirectStatistics> failures = statisticsService.getRecentFailures(hours);
            return ResponseEntity.ok(failures);
        } catch (Exception e) {
            log.error("Ошибка получения неудач: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Проверка работоспособности сервиса статистики
     */
    @GetMapping("/api/health")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean healthy = statisticsService.isHealthy();
        
        return ResponseEntity.ok(Map.of(
            "healthy", healthy,
            "service", "RedirectStatisticsService"
        ));
    }
}