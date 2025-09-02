package com.java.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.FileAnalysisResultDto;
import com.java.model.entity.FileMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Маппер для преобразования FileMetadata между Entity и DTO
 */
@Slf4j
public class FileMetadataMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private FileMetadataMapper() {
        // Утилитный класс
    }

    /**
     * Entity -> FileAnalysisResultDto
     */
    public static FileAnalysisResultDto toAnalysisDto(FileMetadata entity) {
        if (entity == null) return null;

        FileAnalysisResultDto dto = FileAnalysisResultDto.builder()
                .filename(entity.getOriginalFilename())
                .fileSize(entity.getFileSize())
                .fileFormat(entity.getFileFormat())
                .encoding(entity.getDetectedEncoding())
                .delimiter(entity.getDetectedDelimiter())
                .totalColumns(entity.getTotalColumns())
                .hasHeader(entity.getHasHeader())
                .build();

        // Парсим JSON поля
        dto.setColumnHeaders(parseJsonArray(entity.getColumnHeaders()));
        List<Map<String, String>> sampleData = parseSampleData(entity.getSampleData());
        dto.setSampleData(sampleData);
        dto.setTotalRows(sampleData.size()); // Устанавливаем количество строк на основе примеров данных

        return dto;
    }

    /**
     * Парсит JSON массив заголовков
     */
    private static List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();

        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Ошибка парсинга JSON массива", e);
            return new ArrayList<>();
        }
    }

    /**
     * Парсит JSON с примерами данных
     */
    private static List<Map<String, String>> parseSampleData(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();

        try {
            List<List<String>> rawData = objectMapper.readValue(json,
                    new TypeReference<List<List<String>>>() {});

            // Преобразуем в формат с ключами для удобства отображения
            List<Map<String, String>> result = new ArrayList<>();
            for (List<String> row : rawData) {
                Map<String, String> rowMap = new java.util.HashMap<>();
                for (int i = 0; i < row.size(); i++) {
                    rowMap.put("column_" + i, row.get(i));
                }
                result.add(rowMap);
            }
            return result;

        } catch (Exception e) {
            log.error("Ошибка парсинга примеров данных", e);
            return new ArrayList<>();
        }
    }
}
