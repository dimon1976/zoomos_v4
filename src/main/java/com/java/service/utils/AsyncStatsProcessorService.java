package com.java.service.utils;

import com.java.dto.NotificationDto;
import com.java.dto.utils.StatsProcessDto;
import com.java.model.entity.FileMetadata;
import com.java.service.notification.NotificationService;
import com.java.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Сервис для асинхронной обработки файлов статистики
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncStatsProcessorService {

    private final StatsProcessorService statsProcessorService;
    private final PathResolver pathResolver;
    private final NotificationService notificationService;

    /**
     * Асинхронная обработка файла статистики
     */
    @Async("utilsTaskExecutor")
    public void processStatsAsync(FileMetadata metadata, StatsProcessDto dto, String operationId) {
        log.info("Начинаем асинхронную обработку файла статистики: {}", metadata.getOriginalFilename());
        
        try {
            // Обрабатываем файл
            byte[] processedData = statsProcessorService.processStatsFile(metadata, dto);
            
            // Генерируем имя файла с датой и временем
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String extension = "xlsx".equalsIgnoreCase(dto.getOutputFormat()) ? ".xlsx" : ".csv";
            String filename = "stats_processed_" + timestamp + extension;
            
            // Сохраняем в директорию экспорта
            Path exportPath = pathResolver.getAbsoluteExportDir().resolve(filename);
            Files.createDirectories(exportPath.getParent());
            Files.write(exportPath, processedData);
            
            log.info("Файл статистики успешно обработан и сохранён: {}", exportPath.toAbsolutePath());
            
            // Отправляем уведомление об успешном завершении
            notificationService.sendGeneralNotification(
                "Файл обработан успешно! " +
                "Оригинальный файл: " + metadata.getOriginalFilename() + ". " +
                "Результат сохранён: " + filename,
                NotificationDto.NotificationType.SUCCESS
            );
            
        } catch (Exception e) {
            log.error("Ошибка при асинхронной обработке файла статистики: {}", metadata.getOriginalFilename(), e);
            
            // Отправляем уведомление об ошибке
            notificationService.sendGeneralNotification(
                "Не удалось обработать файл " + metadata.getOriginalFilename() + ". " +
                "Ошибка: " + e.getMessage(),
                NotificationDto.NotificationType.ERROR
            );
        }
    }
}