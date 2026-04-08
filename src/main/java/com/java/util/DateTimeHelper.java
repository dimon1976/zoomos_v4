package com.java.util;

import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Утилитарный Spring-компонент для форматирования дат с конвертацией в системную таймзону.
 * Доступен в Thymeleaf-шаблонах как {@code ${@dateTimeHelper.format(field, 'dd.MM.yyyy HH:mm')}}.
 *
 * Решает проблему: ZonedDateTime хранится в БД как UTC, но Thymeleaf's {@code #temporals.format}
 * не выполняет конвертацию таймзоны — время отображается на 3 часа меньше (UTC vs UTC+3).
 */
@Component("dateTimeHelper")
public class DateTimeHelper {

    private static final DateTimeFormatter DEFAULT_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Форматирует дату с конвертацией в системную таймзону. Паттерн по умолчанию: dd.MM.yyyy HH:mm.
     *
     * @return отформатированная строка, или пустая строка если dt == null
     */
    public String format(ZonedDateTime dt) {
        if (dt == null) return "";
        return dt.withZoneSameInstant(ZoneId.systemDefault()).format(DEFAULT_FMT);
    }

    /**
     * Форматирует дату с конвертацией в системную таймзону по заданному паттерну.
     *
     * @return отформатированная строка, или пустая строка если dt == null
     */
    public String format(ZonedDateTime dt, String pattern) {
        if (dt == null) return "";
        return dt.withZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern));
    }
}
