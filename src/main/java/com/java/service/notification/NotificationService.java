package com.java.service.notification;

import com.java.dto.NotificationDto;
import com.java.model.FileOperation;
import com.java.model.entity.ImportSession;
import com.java.model.entity.ExportSession;

/**
 * Сервис для отправки уведомлений пользователям
 */
public interface NotificationService {

    /**
     * Отправить уведомление о завершении импорта
     */
    void sendImportCompletedNotification(ImportSession session, FileOperation operation);

    /**
     * Отправить уведомление о завершении экспорта
     */
    void sendExportCompletedNotification(ExportSession session, FileOperation operation);

    /**
     * Отправить уведомление об ошибке импорта
     */
    void sendImportFailedNotification(ImportSession session, FileOperation operation, String errorMessage);

    /**
     * Отправить уведомление об ошибке экспорта  
     */
    void sendExportFailedNotification(ExportSession session, FileOperation operation, String errorMessage);

    /**
     * Отправить общее уведомление
     */
    void sendGeneralNotification(String message, NotificationDto.NotificationType type);

    /**
     * Отправить персональное уведомление пользователю
     */
    void sendUserNotification(Long userId, NotificationDto notification);
}