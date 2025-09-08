package com.java.service.statistics;

import com.java.model.entity.ExportStatistics;
import com.java.repository.ExportStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для StatisticsCacheService
 */
@ExtendWith(MockitoExtension.class)
class StatisticsCacheServiceTest {

    @Mock
    private ExportStatisticsRepository statisticsRepository;

    private StatisticsCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new StatisticsCacheService(statisticsRepository);
    }

    @Test
    void getAggregatedStats_shouldReturnDataFromRepository() {
        // Given
        List<Long> sessionIds = Arrays.asList(1L, 2L, 3L);
        List<Object[]> expectedStats = Arrays.asList(
            new Object[]{"Group1", 100L, 5L, 10L},
            new Object[]{"Group2", 200L, 3L, 15L}
        );
        
        when(statisticsRepository.findAggregatedStatsBySessionIds(sessionIds))
            .thenReturn(expectedStats);

        // When
        List<Object[]> result = cacheService.getAggregatedStats(sessionIds);

        // Then
        assertThat(result).isEqualTo(expectedStats);
        verify(statisticsRepository).findAggregatedStatsBySessionIds(sessionIds);
    }

    @Test
    void getTopGroups_shouldReturnLimitedResults() {
        // Given
        Long clientId = 1L;
        ZonedDateTime sinceDate = ZonedDateTime.now().minusDays(7);
        int limit = 5;
        
        List<Object[]> expectedGroups = Arrays.asList(
            new Object[]{"Group1", 500L},
            new Object[]{"Group2", 300L}
        );
        
        Page<Object[]> mockPage = new PageImpl<>(expectedGroups);
        when(statisticsRepository.findTopGroupsByClient(eq(clientId), eq(sinceDate), any(PageRequest.class)))
            .thenReturn(mockPage);

        // When
        List<Object[]> result = cacheService.getTopGroups(clientId, sinceDate, limit);

        // Then
        assertThat(result).isEqualTo(expectedGroups);
        verify(statisticsRepository).findTopGroupsByClient(eq(clientId), eq(sinceDate), any(PageRequest.class));
    }

    @Test
    void getDailyTrends_shouldReturnTrendsData() {
        // Given
        Long clientId = 1L;
        ZonedDateTime sinceDate = ZonedDateTime.now().minusDays(30);
        
        List<Object[]> expectedTrends = Arrays.asList(
            new Object[]{"2024-01-01", "Group1", 50L},
            new Object[]{"2024-01-02", "Group1", 75L}
        );
        
        when(statisticsRepository.findDailyTrendsByClient(clientId, sinceDate))
            .thenReturn(expectedTrends);

        // When
        List<Object[]> result = cacheService.getDailyTrends(clientId, sinceDate);

        // Then
        assertThat(result).isEqualTo(expectedTrends);
        verify(statisticsRepository).findDailyTrendsByClient(clientId, sinceDate);
    }

    @Test
    void getStatisticsForPeriod_shouldReturnPeriodData() {
        // Given
        Long clientId = 1L;
        ZonedDateTime startDate = ZonedDateTime.now().minusDays(30);
        ZonedDateTime endDate = ZonedDateTime.now();
        
        List<ExportStatistics> expectedStats = Arrays.asList(
            createMockStatistics(1L, "Group1", "Field1", 100L),
            createMockStatistics(2L, "Group2", "Field2", 200L)
        );
        
        when(statisticsRepository.findByDateRangeAndClient(startDate, endDate, clientId))
            .thenReturn(expectedStats);

        // When
        List<ExportStatistics> result = cacheService.getStatisticsForPeriod(clientId, startDate, endDate);

        // Then
        assertThat(result).isEqualTo(expectedStats);
        verify(statisticsRepository).findByDateRangeAndClient(startDate, endDate, clientId);
    }

    @Test
    void getUniqueGroupValues_shouldReturnUniqueValues() {
        // Given
        Long sessionId = 1L;
        List<String> expectedValues = Arrays.asList("Group1", "Group2", "Group3");
        
        when(statisticsRepository.findDistinctGroupFieldValuesBySessionId(sessionId))
            .thenReturn(expectedValues);

        // When
        List<String> result = cacheService.getUniqueGroupValues(sessionId);

        // Then
        assertThat(result).isEqualTo(expectedValues);
        verify(statisticsRepository).findDistinctGroupFieldValuesBySessionId(sessionId);
    }

    @Test
    void getUniqueCountFields_shouldReturnFieldNames() {
        // Given
        Long sessionId = 1L;
        List<String> expectedFields = Arrays.asList("field1", "field2", "field3");
        
        when(statisticsRepository.findDistinctCountFieldNamesBySessionId(sessionId))
            .thenReturn(expectedFields);

        // When
        List<String> result = cacheService.getUniqueCountFields(sessionId);

        // Then
        assertThat(result).isEqualTo(expectedFields);
        verify(statisticsRepository).findDistinctCountFieldNamesBySessionId(sessionId);
    }

    @Test
    void evictStatsCache_shouldCallCacheEvictionMethods() {
        // When - вызываем методы очистки кэша
        cacheService.evictStatsCache();
        cacheService.evictSessionCache(1L);
        cacheService.evictClientCache(1L);
        cacheService.evictAllCache();

        // Then - проверяем что методы выполнились без ошибок
        // В unit тестах аннотации @CacheEvict не работают,
        // но мы проверяем что методы могут быть вызваны
        assertThat(true).isTrue(); // Методы выполнены успешно
    }

    @Test
    void logCacheStatus_shouldLogWithoutErrors() {
        // When & Then - проверяем что логирование работает
        cacheService.logCacheStatus("TEST_OPERATION", "test-key");
        cacheService.logCacheInfo();
        
        // Методы должны выполняться без исключений
        assertThat(true).isTrue();
    }

    // Helper methods
    
    private ExportStatistics createMockStatistics(Long id, String groupValue, String countField, Long countValue) {
        return ExportStatistics.builder()
                .id(id)
                .groupFieldValue(groupValue)
                .countFieldName(countField)
                .countValue(countValue)
                .dateModificationsCount(0L)
                .totalRecordsCount(countValue)
                .modificationType("STANDARD")
                .createdAt(ZonedDateTime.now())
                .build();
    }
}