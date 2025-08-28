package com.java.service.exports;

import com.java.dto.ExportRequestDto;
import com.java.model.FileOperation;
import com.java.model.entity.ExportSession;
import com.java.repository.FileOperationRepository;
import com.java.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

/**
 * Сервис для асинхронного выполнения экспорта
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncExportService {

    private final ExportProcessorService processorService;
    private final NotificationService notificationService;
    private final FileOperationRepository fileOperationRepository;

    /**
     * Запускает асинхронный экспорт
     */
    @Async("exportTaskExecutor")
    @Transactional
    public CompletableFuture<ExportSession> startAsyncExport(
            ExportSession session,
            ExportRequestDto request) {

        log.info("Запуск асинхронного экспорта для сессии ID: {}", session.getId());

        // Находим связанную операцию через сессию
        FileOperation operation = session.getFileOperation();

        try {
            // Выполняем обработку
            processorService.processExport(session, request);

            // Отправляем уведомление об успешном завершении
            if (operation != null) {
                notificationService.sendExportCompletedNotification(session, operation);
            }
            
            log.info("Асинхронный экспорт для сессии ID: {} завершен успешно", session.getId());
            return CompletableFuture.completedFuture(session);

        } catch (Exception e) {
            log.error("Ошибка асинхронного экспорта для сессии ID: {}", session.getId(), e);
            
            // Отправляем уведомление об ошибке
            if (operation != null) {
                notificationService.sendExportFailedNotification(session, operation, e.getMessage());
            }
            
            return CompletableFuture.failedFuture(e);
        }
    }
}