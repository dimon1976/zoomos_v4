package com.java.dto;

import com.java.model.entity.ErrorLog;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO для WebSocket уведомлений об ошибках.
 * Часть централизованной системы обработки ошибок.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorNotificationDto {

    private Long errorLogId;
    private String errorType;
    private String errorMessage;
    private String requestUri;
    private String severity;
    private String fieldName;
    private String invalidValue;
    private LocalDateTime timestamp;
    private String userFriendlyMessage;
    private String actionUrl;
    private String actionText;
    private boolean critical;

    /**
     * Создает DTO из ErrorLog сущности
     * 
     * @param errorLog запись об ошибке
     * @return DTO уведомления
     */
    public static ErrorNotificationDto fromErrorLog(ErrorLog errorLog) {
        return ErrorNotificationDto.builder()
            .errorLogId(errorLog.getId())
            .errorType(errorLog.getErrorType())
            .errorMessage(errorLog.getErrorMessage())
            .requestUri(errorLog.getRequestUri())
            .severity(errorLog.getSeverity().name())
            .fieldName(errorLog.getFieldName())
            .invalidValue(errorLog.getInvalidValue())
            .timestamp(errorLog.getCreatedAt())
            .critical(errorLog.getSeverity() == ErrorLog.ErrorSeverity.CRITICAL || 
                     errorLog.getSeverity() == ErrorLog.ErrorSeverity.HIGH)
            .userFriendlyMessage(generateUserFriendlyMessage(errorLog))
            .actionUrl(generateActionUrl(errorLog))
            .actionText(generateActionText(errorLog))
            .build();
    }

    /**
     * Генерирует пользовательское сообщение об ошибке
     * 
     * @param errorLog запись об ошибке
     * @return пользовательское сообщение
     */
    private static String generateUserFriendlyMessage(ErrorLog errorLog) {
        return switch (errorLog.getErrorType()) {
            case "ValidationException" -> "Ошибка валидации данных";
            case "FileOperationException" -> "Ошибка обработки файла";
            case "EntityNotFoundException" -> "Объект не найден";
            case "AccessDeniedException" -> "Доступ запрещен";
            case "MaxUploadSizeExceededException" -> "Превышен размер файла";
            case "IOException" -> "Ошибка файловой операции";
            default -> "Произошла ошибка в системе";
        };
    }

    /**
     * Генерирует URL для действия по исправлению ошибки
     * 
     * @param errorLog запись об ошибке
     * @return URL действия или null
     */
    private static String generateActionUrl(ErrorLog errorLog) {
        if (errorLog.getRequestUri() == null) {
            return null;
        }

        String uri = errorLog.getRequestUri();
        
        if (uri.contains("/clients") && errorLog.getErrorType().equals("ValidationException")) {
            return uri; // Вернуться к форме создания/редактирования клиента
        } else if (uri.contains("/import")) {
            // Извлечь clientId из URI для возврата к импорту
            String[] parts = uri.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("import".equals(parts[i]) && i + 1 < parts.length) {
                    return "/import/" + parts[i + 1];
                }
            }
            return "/import";
        }
        
        return null;
    }

    /**
     * Генерирует текст действия для исправления ошибки
     * 
     * @param errorLog запись об ошибке
     * @return текст действия или null
     */
    private static String generateActionText(ErrorLog errorLog) {
        if (generateActionUrl(errorLog) == null) {
            return null;
        }

        return switch (errorLog.getErrorType()) {
            case "ValidationException" -> "Исправить данные";
            case "FileOperationException" -> "Попробовать снова";
            case "MaxUploadSizeExceededException" -> "Выбрать другой файл";
            default -> "Перейти";
        };
    }
}