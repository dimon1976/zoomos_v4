package com.java.service.utils;

import com.java.dto.utils.RedirectCollectorDto;
import com.java.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Сервис для асинхронного выполнения сбора редиректов
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncRedirectCollectorService {

    private final RedirectCollectorService redirectCollectorService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Запускает асинхронный сбор редиректов с уведомлениями о прогрессе
     */
    @Async("utilsTaskExecutor")
    public CompletableFuture<String> startAsyncRedirectCollection(
            String operationId,
            RedirectCollectorDto dto) {

        log.info("Запуск асинхронного сбора редиректов для операции ID: {}", operationId);

        try {
            // Уведомляем о начале
            sendProgressNotification(operationId, 0, "Начинаем сбор редиректов...");
            notificationService.sendGeneralNotification(
                "Начат сбор финальных ссылок (ID: " + operationId + ")", 
                com.java.dto.NotificationDto.NotificationType.INFO
            );

            // Создаем кастомную версию с прогресс-уведомлениями
            String resultFilePath = processWithProgress(operationId, dto);

            // Уведомляем о завершении
            sendProgressNotification(operationId, 100, "Сбор редиректов завершен!");
            notificationService.sendGeneralNotification(
                "Сбор финальных ссылок завершен успешно (ID: " + operationId + ")", 
                com.java.dto.NotificationDto.NotificationType.SUCCESS
            );

            return CompletableFuture.completedFuture(resultFilePath);

        } catch (Exception e) {
            log.error("Ошибка при асинхронном сборе редиректов для операции {}: {}", operationId, e.getMessage(), e);
            
            // Уведомляем об ошибке
            sendProgressNotification(operationId, -1, "Ошибка: " + e.getMessage());
            notificationService.sendGeneralNotification(
                "Ошибка при сборе финальных ссылок (ID: " + operationId + "): " + e.getMessage(), 
                com.java.dto.NotificationDto.NotificationType.ERROR
            );

            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Обработка с прогресс-уведомлениями
     */
    private String processWithProgress(String operationId, RedirectCollectorDto dto) throws Exception {
        // Создаем кастомную версию processRedirectCollection с прогресс-callback
        return redirectCollectorService.processRedirectCollectionWithProgress(dto, 
            (current, total, message) -> {
                int progress = total > 0 ? (int) ((current * 100.0) / total) : 0;
                sendProgressNotification(operationId, progress, message);
                log.info("Прогресс {}/{} ({}%): {}", current, total, progress, message);
            }
        );
    }

    /**
     * Отправляет уведомление о прогрессе через WebSocket
     */
    private void sendProgressNotification(String operationId, int progress, String message) {
        try {
            var notification = new ProgressNotification(operationId, progress, message);
            messagingTemplate.convertAndSend("/topic/redirect-progress/" + operationId, notification);
        } catch (Exception e) {
            log.warn("Не удалось отправить уведомление о прогрессе: {}", e.getMessage());
        }
    }

    /**
     * DTO для уведомлений о прогрессе
     */
    public static class ProgressNotification {
        public final String operationId;
        public final int progress;
        public final String message;
        public final long timestamp;

        public ProgressNotification(String operationId, int progress, String message) {
            this.operationId = operationId;
            this.progress = progress;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }
}