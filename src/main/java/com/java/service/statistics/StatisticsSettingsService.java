// src/main/java/com/java/service/statistics/StatisticsSettingsService.java
package com.java.service.statistics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class StatisticsSettingsService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Получает процент отклонения для предупреждения
     */
    public Integer getWarningPercentage() {
        return getIntegerSetting("deviation_percentage_warning", 10);
    }

    /**
     * Получает процент отклонения для критического уровня
     */
    public Integer getCriticalPercentage() {
        return getIntegerSetting("deviation_percentage_critical", 20);
    }

    /**
     * Получает максимальное количество операций для сравнения
     */
    public Integer getMaxOperations() {
        return getIntegerSetting("statistics_max_operations", 10);
    }

    /**
     * Обновляет настройку
     */
    public void updateSetting(String key, String value) {
        log.info("Обновление настройки: {} = {}", key, value);

        String sql = "UPDATE statistics_settings SET setting_value = ?, updated_at = CURRENT_TIMESTAMP WHERE setting_key = ?";
        int updated = jdbcTemplate.update(sql, value, key);

        if (updated == 0) {
            log.warn("Настройка {} не найдена", key);
        }
    }

    /**
     * Получает все настройки
     */
    public Map<String, String> getAllSettings() {
        String sql = "SELECT setting_key, setting_value FROM statistics_settings";

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                        Map.entry(rs.getString("setting_key"), rs.getString("setting_value")))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    /**
     * Получает настройку как строку
     */
    public String getStringSetting(String key, String defaultValue) {
        try {
            String sql = "SELECT setting_value FROM statistics_settings WHERE setting_key = ?";
            return jdbcTemplate.queryForObject(sql, String.class, key);
        } catch (Exception e) {
            log.warn("Не удалось получить настройку {}, используется значение по умолчанию: {}", key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Получает настройку как число
     */
    public Integer getIntegerSetting(String key, Integer defaultValue) {
        try {
            String value = getStringSetting(key, String.valueOf(defaultValue));
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Неверный формат числа для настройки {}, используется значение по умолчанию: {}", key, defaultValue);
            return defaultValue;
        }
    }
}