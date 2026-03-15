package com.java.util;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Утилита для работы с cron-выражениями.
 * Нумерация дней недели: 1=Вс, 2=Пн, 3=Вт, 4=Ср, 5=Чт, 6=Пт, 7=Сб.
 */
public final class CronUtils {

    private CronUtils() {}

    /**
     * Конвертирует cron в 6-польный Spring формат.
     * 5-польный Unix cron получает "0 " (секунды) в начале.
     * Числа дня недели (поле 6) уменьшаются на 1: пользовательские 1–7 → Spring 0–6.
     */
    public static String toSpringCron(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length == 5) {
            parts = ("0 " + raw.trim()).split("\\s+");
        }
        if (parts.length == 6) {
            parts[5] = convertDowField(parts[5]);
        }
        return String.join(" ", parts);
    }

    /**
     * Вычитает 1 из числовых значений дня недели: 1–7 (пользователь) → 0–6 (Spring).
     * Текстовые алиасы (MON, TUE, SUN и т.д.) не изменяются.
     */
    private static String convertDowField(String field) {
        if (field.equals("*")) return field;
        return Arrays.stream(field.split(","))
                .map(part -> {
                    if (part.contains("-")) {
                        String[] b = part.split("-", 2);
                        try { return (Integer.parseInt(b[0]) - 1) + "-" + (Integer.parseInt(b[1]) - 1); }
                        catch (NumberFormatException e) { return part; }
                    }
                    if (part.startsWith("*/")) return part;
                    try { return String.valueOf(Integer.parseInt(part) - 1); }
                    catch (NumberFormatException e) { return part; }
                })
                .collect(Collectors.joining(","));
    }
}
