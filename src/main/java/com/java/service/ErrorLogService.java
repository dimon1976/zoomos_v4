package com.java.service;

import com.java.model.entity.ErrorLog;
import com.java.repository.ErrorLogRepository;
import com.java.service.validation.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для работы с логами ошибок.
 * Часть централизованной системы обработки ошибок.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ErrorLogService {

    private final ErrorLogRepository errorLogRepository;

    /**
     * Логирует ошибку в базу данных
     * 
     * @param throwable исключение для логирования
     * @param request HTTP-запрос (может быть null)
     * @return созданная запись об ошибке
     */
    @Transactional
    public ErrorLog logError(Throwable throwable, HttpServletRequest request) {
        return logError(throwable, request, null);
    }

    /**
     * Логирует ошибку в базу данных с дополнительной контекстной информацией
     * 
     * @param throwable исключение для логирования
     * @param request HTTP-запрос (может быть null)
     * @param contextData дополнительная информация
     * @return созданная запись об ошибке
     */
    @Transactional
    public ErrorLog logError(Throwable throwable, HttpServletRequest request, String contextData) {
        try {
            ErrorLog.ErrorLogBuilder builder = ErrorLog.builder()
                .errorType(throwable.getClass().getSimpleName())
                .errorMessage(truncate(throwable.getMessage(), 1000))
                .stackTrace(truncate(getStackTrace(throwable), 5000))
                .severity(determineSeverity(throwable))
                .contextData(truncate(contextData, 2000));

            // Добавляем информацию из запроса, если она доступна
            if (request != null) {
                builder.requestUri(truncate(request.getRequestURI(), 500))
                      .httpMethod(request.getMethod())
                      .userAgent(truncate(request.getHeader("User-Agent"), 1000))
                      .clientIp(getClientIp(request));
            }

            // Добавляем специфичную информацию для ValidationException
            if (throwable instanceof ValidationException validationEx) {
                builder.fieldName(truncate(validationEx.getFieldName(), 100))
                      .invalidValue(truncate(String.valueOf(validationEx.getInvalidValue()), 500));
            }

            ErrorLog errorLog = builder.build();
            ErrorLog savedLog = errorLogRepository.save(errorLog);
            
            log.debug("Ошибка сохранена в БД с ID: {}, тип: {}", 
                     savedLog.getId(), savedLog.getErrorType());
            
            return savedLog;
            
        } catch (Exception e) {
            // Если не удалось сохранить ошибку в БД, логируем это в обычные логи
            log.error("Не удалось сохранить ошибку в БД", e);
            log.error("Исходная ошибка: {}", throwable.getMessage(), throwable);
            
            // Возвращаем временную запись для совместимости
            return ErrorLog.builder()
                .id(-1L)
                .errorType(throwable.getClass().getSimpleName())
                .errorMessage(throwable.getMessage())
                .createdAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Определяет уровень серьезности ошибки
     * 
     * @param throwable исключение
     * @return уровень серьезности
     */
    private ErrorLog.ErrorSeverity determineSeverity(Throwable throwable) {
        String className = throwable.getClass().getSimpleName();
        
        return switch (className) {
            case "ValidationException" -> ErrorLog.ErrorSeverity.LOW;
            case "FileOperationException", "IOException" -> ErrorLog.ErrorSeverity.MEDIUM;
            case "EntityNotFoundException" -> ErrorLog.ErrorSeverity.LOW;
            case "AccessDeniedException" -> ErrorLog.ErrorSeverity.HIGH;
            case "MaxUploadSizeExceededException" -> ErrorLog.ErrorSeverity.MEDIUM;
            case "RuntimeException", "IllegalStateException" -> ErrorLog.ErrorSeverity.HIGH;
            case "OutOfMemoryError", "StackOverflowError" -> ErrorLog.ErrorSeverity.CRITICAL;
            default -> ErrorLog.ErrorSeverity.MEDIUM;
        };
    }

    /**
     * Извлекает IP-адрес клиента из запроса
     * 
     * @param request HTTP-запрос
     * @return IP-адрес клиента
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Получает стек-трейс как строку
     * 
     * @param throwable исключение
     * @return стек-трейс как строка
     */
    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Обрезает строку до указанной длины
     * 
     * @param str строка для обрезки
     * @param maxLength максимальная длина
     * @return обрезанная строка
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        
        if (str.length() <= maxLength) {
            return str;
        }
        
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Получает все ошибки с пагинацией
     * 
     * @param pageable пагинация
     * @return страница ошибок
     */
    public Page<ErrorLog> getAllErrors(Pageable pageable) {
        return errorLogRepository.findAll(pageable);
    }

    /**
     * Получает ошибки по типу
     * 
     * @param errorType тип ошибки
     * @param pageable пагинация
     * @return страница ошибок
     */
    public Page<ErrorLog> getErrorsByType(String errorType, Pageable pageable) {
        return errorLogRepository.findByErrorType(errorType, pageable);
    }

    /**
     * Получает критичные ошибки за последние часы
     * 
     * @param hours количество часов
     * @return список критичных ошибок
     */
    public List<ErrorLog> getRecentCriticalErrors(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return errorLogRepository.findRecentCriticalErrors(ErrorLog.ErrorSeverity.CRITICAL, since);
    }

    /**
     * Получает ошибки, для которых не отправлены уведомления
     * 
     * @return список ошибок без уведомлений
     */
    public List<ErrorLog> getErrorsWithoutNotification() {
        return errorLogRepository.findByNotificationSentFalse();
    }

    /**
     * Отмечает ошибку как уведомление отправлено
     * 
     * @param errorLogId ID записи об ошибке
     */
    @Transactional
    public void markNotificationSent(Long errorLogId) {
        errorLogRepository.findById(errorLogId).ifPresent(errorLog -> {
            errorLog.setNotificationSent(true);
            errorLogRepository.save(errorLog);
            log.debug("Уведомление отмечено как отправленное для ошибки ID: {}", errorLogId);
        });
    }

    /**
     * Очищает старые логи ошибок
     * 
     * @param daysToKeep количество дней для хранения
     * @return количество удаленных записей
     */
    @Transactional
    public int cleanOldLogs(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        
        // Сохраняем критичные ошибки
        List<ErrorLog.ErrorSeverity> preserveSeverities = List.of(
            ErrorLog.ErrorSeverity.CRITICAL,
            ErrorLog.ErrorSeverity.HIGH
        );
        
        int deletedCount = errorLogRepository.deleteOldLogs(cutoffDate, preserveSeverities);
        log.info("Удалено {} старых записей об ошибках (старше {} дней)", deletedCount, daysToKeep);
        
        return deletedCount;
    }
}