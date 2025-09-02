package com.java.dto.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO для загрузки файлов в утилиты
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UtilFileUploadDto {

    @NotNull(message = "Файл обязателен для загрузки")
    private MultipartFile file;

    @Size(max = 255, message = "Описание файла не должно превышать 255 символов")
    private String description;

    // Дополнительные параметры загрузки при необходимости
    private String encoding; // Принудительное указание кодировки
    private String delimiter; // Принудительное указание разделителя для CSV
}