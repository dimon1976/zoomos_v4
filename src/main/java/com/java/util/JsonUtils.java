package com.java.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Утилиты для работы с JSON
 */
@Slf4j
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {
        // Utility class
    }

    /**
     * Парсит JSON строку в список строк
     *
     * @param json JSON строка с массивом строк (например: ["value1", "value2"])
     * @return Список строк или пустой список при ошибке парсинга
     */
    public static List<String> parseJsonStringList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return List.of();
        }

        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Ошибка парсинга JSON списка: {}", json, e);
            return List.of();
        }
    }
}
