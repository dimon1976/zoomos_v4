package com.java.controller;

import com.java.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
            Path baseDir = pathResolver.getAbsoluteExportDir().toRealPath(LinkOption.NOFOLLOW_LINKS);

            // SEC-002: нормализация пути предотвращает path traversal через ../
            Path filePath = baseDir.resolve(filename).normalize();

            // Проверяем существование файла
            if (!Files.exists(filePath)) {
                log.error("Файл не найден: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            // Проверяем, что файл находится в директории экспорта (после нормализации)
            Path realFilePath = filePath.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realFilePath.startsWith(baseDir)) {
                log.error("Попытка path traversal: {}", filename);
                return ResponseEntity.status(403).build();
            }

            Resource resource = new FileSystemResource(realFilePath);

            // Определяем тип контента
            MediaType contentType = getContentType(filename);

            // SEC-006: безопасный Content-Disposition без возможности header injection
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(realFilePath.getFileName().toString(), StandardCharsets.UTF_8)
                    .build();

            log.info("Скачивается файл: {}", filename);

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
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