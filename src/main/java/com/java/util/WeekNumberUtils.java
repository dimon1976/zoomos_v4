package com.java.util;

import lombok.extern.slf4j.Slf4j;

import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;

/**
 * Утилиты для работы с номерами недель
 */
@Slf4j
public class WeekNumberUtils {

    private WeekNumberUtils() {
        // Utility class
    }

    /**
     * Возвращает номер недели в году по стандарту ISO 8601
     *
     * @param dateTime Дата и время для расчета
     * @return Номер недели в формате "W01", "W02", ..., "W52"/"W53"
     */
    public static String getWeekNumber(ZonedDateTime dateTime) {
        int weekNumber = dateTime.get(WeekFields.ISO.weekOfWeekBasedYear());
        return String.format("W%02d", weekNumber);
    }

    /**
     * Возвращает номер недели для текущего момента
     *
     * @return Номер недели в формате "W01", "W02", ..., "W52"/"W53"
     */
    public static String getCurrentWeekNumber() {
        return getWeekNumber(ZonedDateTime.now());
    }
}
