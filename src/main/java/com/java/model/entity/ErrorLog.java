package com.java.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Сущность для логирования ошибок в базе данных.
 * Часть централизованной системы обработки ошибок.
 */
@Entity
@Table(name = "error_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Тип ошибки (ValidationException, FileOperationException, etc.)
     */
    @Column(name = "error_type", nullable = false, length = 100)
    private String errorType;

    /**
     * Сообщение об ошибке
     */
    @Column(name = "error_message", nullable = false, length = 1000)
    private String errorMessage;

    /**
     * Стек-трейс ошибки (сокращенный)
     */
    @Column(name = "stack_trace", length = 5000)
    private String stackTrace;

    /**
     * URI запроса, где произошла ошибка
     */
    @Column(name = "request_uri", length = 500)
    private String requestUri;

    /**
     * HTTP-метод запроса
     */
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    /**
     * User-Agent клиента
     */
    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    /**
     * IP-адрес клиента
     */
    @Column(name = "client_ip", length = 45)
    private String clientIp;

    /**
     * Имя поля (для ValidationException)
     */
    @Column(name = "field_name", length = 100)
    private String fieldName;

    /**
     * Некорректное значение (для ValidationException)
     */
    @Column(name = "invalid_value", length = 500)
    private String invalidValue;

    /**
     * Дополнительная контекстная информация в формате JSON
     */
    @Column(name = "context_data", length = 2000)
    private String contextData;

    /**
     * Время создания записи об ошибке
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Было ли отправлено уведомление об ошибке
     */
    @Column(name = "notification_sent", nullable = false)
    @Builder.Default
    private Boolean notificationSent = false;

    /**
     * Уровень серьезности ошибки
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    @Builder.Default
    private ErrorSeverity severity = ErrorSeverity.MEDIUM;

    /**
     * Статус обработки ошибки
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ErrorStatus status = ErrorStatus.NEW;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Перечисление уровней серьезности ошибок
     */
    public enum ErrorSeverity {
        LOW,     // Незначительные ошибки валидации
        MEDIUM,  // Обычные ошибки обработки
        HIGH,    // Критичные ошибки системы
        CRITICAL // Фатальные ошибки
    }

    /**
     * Перечисление статусов обработки ошибок
     */
    public enum ErrorStatus {
        NEW,        // Новая ошибка
        REVIEWED,   // Просмотрена администратором
        RESOLVED,   // Решена
        IGNORED     // Проигнорирована
    }
}