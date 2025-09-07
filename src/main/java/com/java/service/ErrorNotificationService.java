package com.java.service;

import com.java.dto.ErrorNotificationDto;
import com.java.model.entity.ErrorLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Сервис для отправки WebSocket уведомлений об ошибках.
 * Часть централизованной системы обработки ошибок.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ErrorNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ErrorLogService errorLogService;

    /**
     * Отправляет уведомление об ошибке через WebSocket
     * 
     * @param errorLog запись об ошибке
     */
    public void sendErrorNotification(ErrorLog errorLog) {
        try {
            if (errorLog == null || errorLog.getId() == null || errorLog.getId() <= 0) {
                log.warn("Невозможно отправить уведомление об ошибке: некорректная запись");
                return;
            }

            ErrorNotificationDto notification = ErrorNotificationDto.fromErrorLog(errorLog);
            
            // Отправляем общее уведомление
            messagingTemplate.convertAndSend("/topic/error-notifications", notification);
            
            // Отправляем критичные ошибки отдельно
            if (notification.isCritical()) {
                messagingTemplate.convertAndSend("/topic/critical-errors", notification);
                log.warn("Отправлено уведомление о критичной ошибке: {}", notification.getErrorType());
            }
            
            // Отмечаем уведомление как отправленное
            errorLogService.markNotificationSent(errorLog.getId());
            
            log.debug("Отправлено WebSocket уведомление об ошибке ID: {}, тип: {}", 
                     errorLog.getId(), errorLog.getErrorType());
                     
        } catch (Exception e) {
            log.error("Ошибка при отправке WebSocket уведомления об ошибке ID: {}", 
                     errorLog.getId(), e);
        }
    }

    /**
     * Отправляет уведомление об ошибке с дополнительными деталями
     * 
     * @param errorLog запись об ошибке
     * @param additionalMessage дополнительное сообщение
     * @param actionUrl URL действия для исправления
     * @param actionText текст действия
     */
    public void sendErrorNotification(ErrorLog errorLog, String additionalMessage, 
                                    String actionUrl, String actionText) {
        try {
            if (errorLog == null || errorLog.getId() == null || errorLog.getId() <= 0) {
                log.warn("Невозможно отправить уведомление об ошибке: некорректная запись");
                return;
            }

            ErrorNotificationDto notification = ErrorNotificationDto.fromErrorLog(errorLog);
            
            // Переопределяем дополнительные поля, если они переданы
            if (additionalMessage != null && !additionalMessage.trim().isEmpty()) {
                notification.setUserFriendlyMessage(additionalMessage);
            }
            
            if (actionUrl != null && !actionUrl.trim().isEmpty()) {
                notification.setActionUrl(actionUrl);
            }
            
            if (actionText != null && !actionText.trim().isEmpty()) {
                notification.setActionText(actionText);
            }
            
            // Отправляем уведомление
            sendErrorNotification(errorLog);
            
        } catch (Exception e) {
            log.error("Ошибка при отправке кастомного WebSocket уведомления об ошибке ID: {}", 
                     errorLog.getId(), e);
        }
    }

    /**
     * Отправляет простое уведомление об ошибке валидации
     * 
     * @param message сообщение об ошибке
     * @param fieldName поле с ошибкой
     * @param requestUri URI запроса
     */
    public void sendValidationErrorNotification(String message, String fieldName, String requestUri) {
        try {
            ErrorNotificationDto notification = ErrorNotificationDto.builder()
                .errorType("ValidationException")
                .errorMessage(message)
                .fieldName(fieldName)
                .requestUri(requestUri)
                .severity("LOW")
                .timestamp(java.time.LocalDateTime.now())
                .userFriendlyMessage("Ошибка валидации данных")
                .critical(false)
                .build();

            messagingTemplate.convertAndSend("/topic/error-notifications", notification);
            
            log.debug("Отправлено уведомление о валидации: поле '{}', сообщение '{}'", 
                     fieldName, message);
                     
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления о валидации", e);
        }
    }

    /**
     * Отправляет уведомление о системной ошибке
     * 
     * @param message сообщение об ошибке
     * @param errorType тип ошибки
     * @param critical является ли ошибка критичной
     */
    public void sendSystemErrorNotification(String message, String errorType, boolean critical) {
        try {
            ErrorNotificationDto notification = ErrorNotificationDto.builder()
                .errorType(errorType)
                .errorMessage(message)
                .severity(critical ? "CRITICAL" : "MEDIUM")
                .timestamp(java.time.LocalDateTime.now())
                .userFriendlyMessage("Произошла системная ошибка")
                .critical(critical)
                .build();

            messagingTemplate.convertAndSend("/topic/error-notifications", notification);
            
            if (critical) {
                messagingTemplate.convertAndSend("/topic/critical-errors", notification);
            }
            
            log.debug("Отправлено уведомление о системной ошибке: {}", errorType);
                     
        } catch (Exception e) {
            log.error("Ошибка при отправке системного уведомления", e);
        }
    }

    /**
     * Проверяет и отправляет уведомления для всех необработанных ошибок
     * Полезно для фоновой обработки пропущенных уведомлений
     */
    public void processPendingErrorNotifications() {
        try {
            var pendingErrors = errorLogService.getErrorsWithoutNotification();
            
            log.debug("Найдено {} необработанных ошибок для отправки уведомлений", 
                     pendingErrors.size());
            
            for (ErrorLog errorLog : pendingErrors) {
                sendErrorNotification(errorLog);
                
                // Добавляем небольшую задержку, чтобы не перегружать WebSocket
                Thread.sleep(100);
            }
            
            if (!pendingErrors.isEmpty()) {
                log.info("Обработано {} уведомлений об ошибках", pendingErrors.size());
            }
            
        } catch (Exception e) {
            log.error("Ошибка при обработке необработанных уведомлений об ошибках", e);
        }
    }
}