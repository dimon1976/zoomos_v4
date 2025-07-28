package com.java.service;

import com.java.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Сервис для периодической очистки временных файлов и данных
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CleanupService {

    private final PathResolver pathResolver;

    /**
     * Очистка временных файлов старше 24 часов
     * Запускается каждый час
     */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupTempFiles() {
        log.debug("Запуск очистки временных файлов");

        Path tempDir = pathResolver.getAbsoluteTempDir();
        if (!Files.exists(tempDir)) {
            return;
        }

        try {
            Files.walkFileTree(tempDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // Проверяем возраст файла
                    Instant fileTime = attrs.creationTime().toInstant();
                    Instant cutoffTime = Instant.now().minus(24, ChronoUnit.HOURS);

                    if (fileTime.isBefore(cutoffTime)) {
                        try {
                            Files.delete(file);
                            log.debug("Удален временный файл: {}", file.getFileName());
                        } catch (IOException e) {
                            log.warn("Не удалось удалить файл {}: {}", file, e.getMessage());
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            log.info("Очистка временных файлов завершена");

        } catch (IOException e) {
            log.error("Ошибка при очистке временных файлов", e);
        }
    }

    /**
     * Очистка файлов импорта старше 7 дней
     * Запускается каждый день в 2 часа ночи
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupImportFiles() {
        log.debug("Запуск очистки файлов импорта");

        Path importDir = pathResolver.getAbsoluteImportDir();
        if (!Files.exists(importDir)) {
            return;
        }

        try {
            Files.walkFileTree(importDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // Проверяем возраст файла
                    Instant fileTime = attrs.creationTime().toInstant();
                    Instant cutoffTime = Instant.now().minus(7, ChronoUnit.DAYS);

                    if (fileTime.isBefore(cutoffTime)) {
                        try {
                            Files.delete(file);
                            log.debug("Удален старый файл импорта: {}", file.getFileName());
                        } catch (IOException e) {
                            log.warn("Не удалось удалить файл {}: {}", file, e.getMessage());
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            log.info("Очистка файлов импорта завершена");

        } catch (IOException e) {
            log.error("Ошибка при очистке файлов импорта", e);
        }
    }

    /**
     * Ручная очистка всех временных файлов
     */
    public void cleanupAllTempFiles() {
        log.info("Запуск полной очистки временных файлов");

        Path tempDir = pathResolver.getAbsoluteTempDir();
        if (!Files.exists(tempDir)) {
            return;
        }

        try {
            Files.walkFileTree(tempDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (!dir.equals(tempDir)) {
                        Files.delete(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            log.info("Полная очистка временных файлов завершена");

        } catch (IOException e) {
            log.error("Ошибка при полной очистке временных файлов", e);
        }
    }
}