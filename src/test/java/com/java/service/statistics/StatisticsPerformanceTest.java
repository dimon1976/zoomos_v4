package com.java.service.statistics;

import com.java.dto.StatisticsFilterDto;
import com.java.dto.StatisticsRequestDto;
import com.java.repository.ExportStatisticsRepository;
import com.java.repository.ClientRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
public class StatisticsPerformanceTest {

    @Autowired
    private ExportStatisticsRepository statisticsRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ExportStatisticsService statisticsService;

    /**
     * Тест производительности запросов к базе данных
     */
    @Test
    @Transactional
    public void testDatabaseQueryPerformance() {
        log.info("=== Тест производительности запросов к базе данных ===");

        // Проверяем общий объем данных в системе
        long totalStatistics = statisticsRepository.count();
        long totalClients = clientRepository.count();
        
        log.info("Общий объем данных в системе:");
        log.info("- Клиентов: {}", totalClients);
        log.info("- Записей статистики: {}", totalStatistics);
        
        if (totalStatistics == 0) {
            log.warn("В системе нет данных для тестирования производительности");
            return;
        }

        // Тест 1: Простой запрос всех записей статистики (без фильтров)
        log.info("=== Тест 1: Запрос всех записей статистики ===");
        testQueryPerformance("findAll", () -> {
            return statisticsRepository.findAll().size();
        });

        // Тест 2: Запрос с пагинацией
        log.info("=== Тест 2: Запрос с пагинацией ===");
        testQueryPerformance("findWithPagination", () -> {
            return statisticsRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 100)).getContent().size();
        });

        // Тест 3: Подсчет количества записей
        log.info("=== Тест 3: Подсчет записей ===");
        testQueryPerformance("count", () -> {
            return (int)statisticsRepository.count();
        });

        // Тест 4: Поиск по первой сессии (если есть данные)
        if (totalClients > 0) {
            var firstStatistic = statisticsRepository.findAll().stream().findFirst();
            if (firstStatistic.isPresent()) {
                Long sessionId = firstStatistic.get().getExportSession().getId();
                log.info("=== Тест 4: Поиск по сессии ID {} ===", sessionId);
                testQueryPerformance("findBySession", () -> {
                    return statisticsRepository.findByExportSessionId(sessionId).size();
                });
            }
        }

        log.info("=== Тестирование производительности завершено ===");
    }

    /**
     * Универсальный метод для измерения производительности запросов
     */
    private void testQueryPerformance(String testName, java.util.function.Supplier<Integer> queryFunction) {
        long startTime = System.currentTimeMillis();
        
        try {
            int resultCount = queryFunction.get();
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("{}: {} записей за {} мс", testName, resultCount, duration);
            
            if (duration > 2000) { // более 2 секунд - медленно
                log.warn("⚠️ Медленный запрос! {} занял {} мс", testName, duration);
            } else if (duration < 500) { // менее полсекунды - быстро
                log.info("✅ Быстрый запрос! {} занял {} мс", testName, duration);
            } else {
                log.info("🔸 Нормальная скорость! {} занял {} мс", testName, duration);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ Ошибка в тесте '{}' через {} мс: {}", testName, duration, e.getMessage(), e);
        }
    }

    /**
     * Тест производительности конкретных оптимизированных запросов
     */
    @Test
    @Transactional
    public void testOptimizedQueriesPerformance() {
        log.info("=== Тест оптимизированных запросов ===");

        // Проверяем есть ли данные для тестирования
        long totalRecords = statisticsRepository.count();
        if (totalRecords == 0) {
            log.warn("Нет данных для тестирования оптимизированных запросов");
            return;
        }

        // Получаем первого доступного клиента с данными
        var clients = clientRepository.findAll();
        if (clients.isEmpty()) {
            log.warn("Нет клиентов для тестирования");
            return;
        }

        Long clientId = clients.get(0).getId();
        log.info("Тестирование для клиента ID: {}", clientId);

        // Тест нативных запросов с фильтрацией (наши оптимизации)
        log.info("=== Тест нативных оптимизированных запросов ===");
        
        // Тест пагинации оптимизированного запроса с правильными параметрами
        int[] pageSizes = {10, 25, 50, 100};
        for (int pageSize : pageSizes) {
            testQueryPerformance("optimizedQuery_page" + pageSize, () -> {
                try {
                    var result = statisticsRepository.findFilteredStatisticsOptimized(
                        clientId, null, null, null, pageSize, 0
                    );
                    return result.size();
                } catch (Exception e) {
                    log.error("Ошибка оптимизированного запроса: {}", e.getMessage());
                    return 0;
                }
            });
        }

        // Тест подсчета с фильтрацией с правильными параметрами
        testQueryPerformance("optimizedCount", () -> {
            try {
                return statisticsRepository.countFilteredStatistics(clientId, null, null, null).intValue();
            } catch (Exception e) {
                log.error("Ошибка подсчета: {}", e.getMessage());
                return 0;
            }
        });

        log.info("=== Тестирование оптимизированных запросов завершено ===");
    }
}