package com.java.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для работы с глобальными настройками Zoomos Check (таблица zoomos_settings).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZoomosSettingsService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, String> getAllSettings() {
        Map<String, String> map = new HashMap<>();
        jdbcTemplate.query("SELECT key, value FROM zoomos_settings", rs -> {
            map.put(rs.getString("key"), rs.getString("value"));
        });
        return map;
    }

    public String getString(String key, String defaultValue) {
        try {
            String val = jdbcTemplate.queryForObject(
                    "SELECT value FROM zoomos_settings WHERE key = ?", String.class, key);
            return (val == null || val.isBlank()) ? defaultValue : val;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public int getInt(String key, int defaultValue) {
        try {
            String val = jdbcTemplate.queryForObject(
                    "SELECT value FROM zoomos_settings WHERE key = ?", String.class, key);
            return val != null ? Integer.parseInt(val) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public void set(String key, String value) {
        jdbcTemplate.update(
                "INSERT INTO zoomos_settings (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value",
                key, value);
        log.debug("ZoomosSettings: {} = {}", key, value);
    }

    public void saveAll(Map<String, String> settings) {
        settings.forEach(this::set);
    }

    /** Возвращает все настройки, ключ которых начинается с prefix. Один SQL-запрос. */
    public Map<String, String> getByPrefix(String prefix) {
        Map<String, String> map = new HashMap<>();
        jdbcTemplate.query(
                "SELECT key, value FROM zoomos_settings WHERE key LIKE ?",
                rs -> { map.put(rs.getString("key"), rs.getString("value")); },
                prefix + "%");
        return map;
    }
}
