package com.java.util;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для WeekNumberUtils
 */
class WeekNumberUtilsTest {

    @Test
    void shouldFormatWeekNumberWithLeadingZero() {
        // Given: Дата в первой неделе года (2024-01-01 - понедельник 1-й недели 2024)
        ZonedDateTime firstWeek = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0,
                ZonedDateTime.now().getZone());

        // When
        String result = WeekNumberUtils.getWeekNumber(firstWeek);

        // Then
        assertEquals("W01", result);
    }

    @Test
    void shouldFormatWeekNumberWithoutLeadingZero() {
        // Given: Дата в 51-й неделе года (2025-12-17 - среда 51-й недели 2025)
        ZonedDateTime fiftiethWeek = ZonedDateTime.of(2025, 12, 17, 12, 0, 0, 0,
                ZonedDateTime.now().getZone());

        // When
        String result = WeekNumberUtils.getWeekNumber(fiftiethWeek);

        // Then
        assertEquals("W51", result);
    }

    @Test
    void shouldHandleLastWeekOfYear() {
        // Given: Дата в последней неделе 2024 года (2024-12-23 - понедельник 52-й недели)
        ZonedDateTime lastDay = ZonedDateTime.of(2024, 12, 23, 12, 0, 0, 0,
                ZonedDateTime.now().getZone());

        // When
        String result = WeekNumberUtils.getWeekNumber(lastDay);

        // Then
        assertEquals("W52", result);
    }

    @Test
    void shouldReturnCurrentWeekNumber() {
        // When
        String result = WeekNumberUtils.getCurrentWeekNumber();

        // Then
        assertNotNull(result);
        assertTrue(result.matches("W\\d{2}"), "Should match pattern W\\d{2}");
    }

    @Test
    void shouldHandleLeapYearWeek() {
        // Given: 29 февраля 2024 (високосный год) - 9-я неделя
        ZonedDateTime leapDay = ZonedDateTime.of(2024, 2, 29, 12, 0, 0, 0,
                ZonedDateTime.now().getZone());

        // When
        String result = WeekNumberUtils.getWeekNumber(leapDay);

        // Then
        assertEquals("W09", result);
    }
}
