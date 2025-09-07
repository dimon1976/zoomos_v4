package com.java.service.exports;

import com.java.dto.ExportRequestDto;
import com.java.model.FileOperation;
import com.java.model.entity.ExportSession;
import com.java.repository.FileOperationRepository;
import com.java.service.notification.NotificationService;
import com.java.service.validation.BusinessValidationService;
import com.java.service.validation.ValidationException;
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
    private final BusinessValidationService businessValidationService;

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
            // Валидация бизнес-правил перед экспортом
            if (operation != null && operation.getClient() != null) {
                businessValidationService.validateClient(operation.getClient());
                log.debug("Клиент {} прошел валидацию для экспорта", operation.getClient().getName());
            }
            
            // Валидация шаблона экспорта
            if (session.getTemplate() != null) {
                businessValidationService.validateTemplate(session.getTemplate());
                log.debug("Шаблон экспорта {} прошел валидацию", session.getTemplate().getName());
            }
            
            // Валидация экспортной операции
            if (operation != null && operation.getClient() != null && session.getTemplate() != null) {
                businessValidationService.validateOperation("EXPORT", 
                    operation.getClient().getId(), 
                    session.getTemplate().getId());
                log.debug("Операция экспорта для клиента {} с шаблоном {} прошла валидацию", 
                         operation.getClient().getName(), session.getTemplate().getName());
            }
            // Выполняем обработку
            processorService.processExport(session, request);

            // Отправляем уведомление об успешном завершении
            if (operation != null) {
                // Перезагружаем операцию с клиентом для нотификации
                FileOperation operationWithClient = fileOperationRepository.findByIdWithClient(operation.getId())
                        .orElse(operation);
                notificationService.sendExportCompletedNotification(session, operationWithClient);
            }
            
            log.info("Асинхронный экспорт для сессии ID: {} завершен успешно", session.getId());
            return CompletableFuture.completedFuture(session);

        } catch (Exception e) {
            log.error("Ошибка асинхронного экспорта для сессии ID: {}", session.getId(), e);
            
            // Отправляем уведомление об ошибке
            if (operation != null) {
                // Перезагружаем операцию с клиентом для нотификации
                FileOperation operationWithClient = fileOperationRepository.findByIdWithClient(operation.getId())
                        .orElse(operation);
                notificationService.sendExportFailedNotification(session, operationWithClient, e.getMessage());
            }
            
            return CompletableFuture.failedFuture(e);
        }
    }
}