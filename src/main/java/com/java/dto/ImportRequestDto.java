package com.java.dto;

import com.java.model.entity.FileMetadata;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportRequestDto {

    @NotNull(message = "Файл обязателен")
    private MultipartFile file;

    @NotNull(message = "ID шаблона обязателен")
    private Long templateId;

    private Boolean validateOnly = false;

    private Boolean asyncMode = true;

    // Путь к уже сохраненному файлу
    private Path savedFilePath;

    // Метаданные файла из предварительного анализа
    private FileMetadata metadata;

    // Переопределение настроек шаблона
    private String delimiter;
    private String encoding;
    private Integer skipHeaderRows;
}
