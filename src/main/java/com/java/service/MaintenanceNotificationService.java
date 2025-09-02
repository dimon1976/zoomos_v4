package com.java.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис уведомлений для системы обслуживания
 * Отправляет уведомления через WebSocket и логирует события
 */
@Service("maintenanceNotificationService")
@Slf4j
@RequiredArgsConstructor
public class MaintenanceNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    
    /**
     * Отправка уведомления о результатах обслуживания
     *
     * @param title заголовок уведомления
     * @param message текст уведомления
     * @param type тип уведомления: success, warning, error, info
     */
    public void sendMaintenanceNotification(String title, String message, String type) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("title", title);
            notification.put("message", message);
            notification.put("type", type);
            notification.put("timestamp", LocalDateTime.now().format(FORMATTER));
            notification.put("category", "maintenance");
            
            // Отправляем через WebSocket для real-time уведомлений
            messagingTemplate.convertAndSend("/topic/notifications", notification);
            
            // Логируем в зависимости от типа
            switch (type.toLowerCase()) {
                case "success":
                    log.info("УВЕДОМЛЕНИЕ [{}]: {}", title, message);
                    break;
                case "warning":
                    log.warn("ПРЕДУПРЕЖДЕНИЕ [{}]: {}", title, message);
                    break;
                case "error":
                    log.error("ОШИБКА [{}]: {}", title, message);
                    break;
                default:
                    log.info("ИНФОРМАЦИЯ [{}]: {}", title, message);
                    break;
            }
            
        } catch (Exception e) {
            log.error("Ошибка отправки уведомления: title={}, message={}", title, message, e);
        }
    }
    
    /**
     * Отправка системного уведомления
     */
    public void sendSystemNotification(String title, String message) {
        sendMaintenanceNotification(title, message, "info");
    }
    
    /**
     * Отправка уведомления об успехе
     */
    public void sendSuccessNotification(String title, String message) {
        sendMaintenanceNotification(title, message, "success");
    }
    
    /**
     * Отправка предупреждения
     */
    public void sendWarningNotification(String title, String message) {
        sendMaintenanceNotification(title, message, "warning");
    }
    
    /**
     * Отправка уведомления об ошибке
     */
    public void sendErrorNotification(String title, String message) {
        sendMaintenanceNotification(title, message, "error");
    }
    
    /**
     * Отправка уведомления о запуске операции обслуживания
     */
    public void notifyMaintenanceStarted(String operation) {
        sendSystemNotification(
            "Запуск обслуживания", 
            "Начато выполнение операции: " + operation
        );
    }
    
    /**
     * Отправка уведомления о завершении операции обслуживания
     */
    public void notifyMaintenanceCompleted(String operation, String details) {
        sendSuccessNotification(
            "Обслуживание завершено", 
            String.format("Операция '%s' завершена успешно. %s", operation, details)
        );
    }
    
    /**
     * Отправка уведомления о критической ситуации в системе
     */
    public void notifyCriticalIssue(String issue, String recommendation) {
        sendErrorNotification(
            "КРИТИЧЕСКАЯ СИТУАЦИЯ", 
            String.format("Проблема: %s. Рекомендация: %s", issue, recommendation)
        );
    }
    
    /**
     * Отправка уведомления о статусе здоровья системы
     */
    public void notifySystemHealth(String status, double score, String recommendation) {
        String type = "success";
        if (score < 60) {
            type = "error";
        } else if (score < 80) {
            type = "warning";
        }
        
        sendMaintenanceNotification(
            "Состояние системы", 
            String.format("Статус: %s, Оценка: %.1f. %s", status, score, recommendation),
            type
        );
    }
}