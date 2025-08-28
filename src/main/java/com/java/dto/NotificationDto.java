package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.ZonedDateTime;

/**
 * DTO для уведомлений пользователю
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    /**
     * Идентификатор уведомления
     */
    private String id;

    /**
     * Заголовок уведомления
     */
    private String title;

    /**
     * Текст сообщения
     */
    private String message;

    /**
     * Тип уведомления
     */
    private NotificationType type;

    /**
     * Время создания уведомления
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private ZonedDateTime timestamp;

    /**
     * Идентификатор связанной операции (если есть)
     */
    private Long operationId;

    /**
     * Имя клиента (если применимо)
     */
    private String clientName;

    /**
     * Имя файла (если применимо)
     */
    private String fileName;

    /**
     * URL для действия (например, просмотр результата)
     */
    private String actionUrl;

    /**
     * Текст кнопки действия
     */
    private String actionText;

    /**
     * Автоматически скрывать уведомление через N секунд (0 = не скрывать)
     */
    private int autoHideSeconds = 8;

    /**
     * Типы уведомлений
     */
    public enum NotificationType {
        SUCCESS("success", "fas fa-check-circle"),
        ERROR("danger", "fas fa-exclamation-triangle"),
        WARNING("warning", "fas fa-exclamation-triangle"),
        INFO("info", "fas fa-info-circle"),
        PROGRESS("primary", "fas fa-spinner fa-spin");

        private final String bootstrapClass;
        private final String iconClass;

        NotificationType(String bootstrapClass, String iconClass) {
            this.bootstrapClass = bootstrapClass;
            this.iconClass = iconClass;
        }

        public String getBootstrapClass() {
            return bootstrapClass;
        }

        public String getIconClass() {
            return iconClass;
        }
    }
}