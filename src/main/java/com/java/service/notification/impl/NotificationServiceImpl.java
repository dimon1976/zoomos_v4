package com.java.service.notification.impl;

import com.java.dto.NotificationDto;
import com.java.model.FileOperation;
import com.java.model.entity.ImportSession;
import com.java.model.entity.ExportSession;
import com.java.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Реализация сервиса уведомлений через WebSocket
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendImportCompletedNotification(ImportSession session, FileOperation operation) {
        log.info("Отправка уведомления о завершении импорта для сессии {}", session.getId());

        NotificationDto notification = NotificationDto.builder()
                .id(UUID.randomUUID().toString())
                .title("Импорт завершен успешно")
                .message(String.format("Импорт файла '%s' для клиента '%s' завершен", 
                        operation.getFileName(),
                        operation.getClient().getName()))
                .type(NotificationDto.NotificationType.SUCCESS)
                .timestamp(ZonedDateTime.now())
                .operationId(operation.getId())
                .clientName(operation.getClient().getName())
                .fileName(operation.getFileName())
                .actionUrl("/import/status/" + operation.getId())
                .actionText("Просмотреть результат")
                .autoHideSeconds(60)
                .build();

        // Отправляем уведомление всем подключенным клиентам
        messagingTemplate.convertAndSend("/topic/notifications", notification);
        
        // Можно также отправить персональное уведомление, если есть система пользователей
        // messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", notification);
    }

    @Override
    public void sendExportCompletedNotification(ExportSession session, FileOperation operation) {
        log.info("Отправка уведомления о завершении экспорта для сессии {}", session.getId());

        NotificationDto notification = NotificationDto.builder()
                .id(UUID.randomUUID().toString())
                .title("Экспорт завершен успешно")
                .message(String.format("Экспорт данных для клиента '%s' завершен",
                        operation.getClient().getName()))
                .type(NotificationDto.NotificationType.SUCCESS)
                .timestamp(ZonedDateTime.now())
                .operationId(operation.getId())
                .clientName(operation.getClient().getName())
                .fileName(operation.getResultFilePath() != null ? operation.getResultFilePath().substring(operation.getResultFilePath().lastIndexOf('/') + 1) : operation.getFileName())
                .actionUrl("/export/download/" + session.getId())
                .actionText("Скачать файл")
                .autoHideSeconds(60)
                .build();

        messagingTemplate.convertAndSend("/topic/notifications", notification);
    }

    @Override
    public void sendImportFailedNotification(ImportSession session, FileOperation operation, String errorMessage) {
        log.info("Отправка уведомления об ошибке импорта для сессии {}", session.getId());

        NotificationDto notification = NotificationDto.builder()
                .id(UUID.randomUUID().toString())
                .title("Ошибка импорта")
                .message(String.format("Импорт файла '%s' завершился с ошибкой: %s", 
                        operation.getFileName(),
                        truncateErrorMessage(errorMessage)))
                .type(NotificationDto.NotificationType.ERROR)
                .timestamp(ZonedDateTime.now())
                .operationId(operation.getId())
                .clientName(operation.getClient().getName())
                .fileName(operation.getFileName())
                .actionUrl("/import/status/" + operation.getId())
                .actionText("Подробности ошибки")
                .autoHideSeconds(0) // Не скрывать автоматически для ошибок
                .build();

        messagingTemplate.convertAndSend("/topic/notifications", notification);
    }

    @Override
    public void sendExportFailedNotification(ExportSession session, FileOperation operation, String errorMessage) {
        log.info("Отправка уведомления об ошибке экспорта для сессии {}", session.getId());

        NotificationDto notification = NotificationDto.builder()
                .id(UUID.randomUUID().toString())
                .title("Ошибка экспорта")
                .message(String.format("Экспорт данных для клиента '%s' завершился с ошибкой: %s", 
                        operation.getClient().getName(),
                        truncateErrorMessage(errorMessage)))
                .type(NotificationDto.NotificationType.ERROR)
                .timestamp(ZonedDateTime.now())
                .operationId(operation.getId())
                .clientName(operation.getClient().getName())
                .fileName(operation.getFileName())
                .actionUrl("/export/status/" + session.getId())
                .actionText("Подробности ошибки")
                .autoHideSeconds(0)
                .build();

        messagingTemplate.convertAndSend("/topic/notifications", notification);
    }

    @Override
    public void sendGeneralNotification(String message, NotificationDto.NotificationType type) {
        log.info("Отправка общего уведомления: {}", message);

        NotificationDto notification = NotificationDto.builder()
                .id(UUID.randomUUID().toString())
                .title(getDefaultTitleForType(type))
                .message(message)
                .type(type)
                .timestamp(ZonedDateTime.now())
                .autoHideSeconds(type == NotificationDto.NotificationType.ERROR ? 0 : 60)
                .build();

        messagingTemplate.convertAndSend("/topic/notifications", notification);
    }

    @Override
    public void sendUserNotification(Long userId, NotificationDto notification) {
        log.info("Отправка персонального уведомления пользователю {}: {}", userId, notification.getMessage());
        
        if (notification.getId() == null) {
            notification.setId(UUID.randomUUID().toString());
        }
        if (notification.getTimestamp() == null) {
            notification.setTimestamp(ZonedDateTime.now());
        }
        
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", notification);
    }

    /**
     * Обрезает сообщение об ошибке до разумной длины
     */
    private String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null) return "Неизвестная ошибка";
        if (errorMessage.length() <= 100) return errorMessage;
        return errorMessage.substring(0, 97) + "...";
    }

    /**
     * Получает заголовок по умолчанию для типа уведомления
     */
    private String getDefaultTitleForType(NotificationDto.NotificationType type) {
        return switch (type) {
            case SUCCESS -> "Успешно";
            case ERROR -> "Ошибка";
            case WARNING -> "Предупреждение";
            case INFO -> "Информация";
            case PROGRESS -> "В процессе";
        };
    }
}