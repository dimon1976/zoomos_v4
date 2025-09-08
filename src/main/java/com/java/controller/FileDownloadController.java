package com.java.controller;

import com.java.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Контроллер для скачивания файлов из директории экспорта
 */
@Controller
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
public class FileDownloadController {

    private final PathResolver pathResolver;

    /**
     * Скачивание файла из директории экспорта
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            // Получаем путь к файлу в директории экспорта
            Path filePath = pathResolver.getAbsoluteExportDir().resolve(filename);
            
            // Проверяем существование файла
            if (!Files.exists(filePath)) {
                log.error("Файл не найден: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            // Проверяем, что файл находится в директории экспорта (безопасность)
            if (!filePath.startsWith(pathResolver.getAbsoluteExportDir())) {
                log.error("Попытка доступа к файлу за пределами директории экспорта: {}", filePath);
                return ResponseEntity.badRequest().build();
            }
            
            Resource resource = new FileSystemResource(filePath);
            
            // Определяем тип контента
            MediaType contentType = getContentType(filename);
            
            log.info("Скачивается файл: {}", filename);
            
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("Ошибка при скачивании файла: {}", filename, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Определение типа контента по расширению файла
     */
    private MediaType getContentType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        
        return switch (extension) {
            case "xlsx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "csv" -> MediaType.parseMediaType("text/csv");
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "txt" -> MediaType.TEXT_PLAIN;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}