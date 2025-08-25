package com.java.service.progress;

import com.java.model.FileOperation;
import com.java.repository.FileOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Базовый сервис для отслеживания прогресса операций
 * @param <T> Тип сессии (ImportSession или ExportSession)
 * @param <P> Тип DTO прогресса (ImportProgressDto или ExportProgressDto)
 */
@Slf4j
@RequiredArgsConstructor
public abstract class BaseProgressService<T, P> {

    protected final FileOperationRepository fileOperationRepository;
    
    // Кеш для отслеживания времени последнего обновления
    protected final ConcurrentHashMap<Long, ZonedDateTime> lastUpdateTimes = new ConcurrentHashMap<>();

    /**
     * Основной метод отправки обновления прогресса
     */
    @Transactional
    public void sendProgressUpdate(T session) {
        log.debug("=== Отправка обновления прогресса для сессии {} ===", getSessionId(session));

        if (!shouldSendUpdate(getSessionId(session))) {
            log.trace("Пропуск обновления - слишком частые вызовы");
            return;
        }

        P progress = buildProgressDto(session);
        log.debug("Создан DTO прогресса: {}% ({}/{})",
                getProgressPercentage(progress),
                getProcessedRecords(progress),
                getTotalRecords(progress));

        // Обновляем связанную операцию
        updateFileOperation(session, progress);

        Long operationId = getOperationId(session);
        log.debug("Отправка через WebSocket для операции ID: {}", operationId);

        try {
            sendWebSocketUpdate(operationId, progress);
            log.info("✓ WebSocket обновление отправлено для сессии {}: {}%",
                    getSessionId(session), getProgressPercentage(progress));
        } catch (Exception e) {
            log.error("✗ Ошибка отправки WebSocket обновления для сессии {}", getSessionId(session), e);
        }
    }

    /**
     * Отправляет уведомление о завершении операции
     */
    @Transactional
    public void sendCompletionNotification(T session) {
        P progress = buildProgressDto(session);
        setUpdateType(progress, "COMPLETED");
        setIsCompleted(progress, true);

        String message = buildCompletionMessage(session);
        setMessage(progress, message);

        Long operationId = getOperationId(session);
        sendWebSocketUpdate(operationId, progress);

        // Очищаем кеш
        cleanupCache(getSessionId(session));

        log.info("Отправлено уведомление о завершении для сессии {}", getSessionId(session));
    }

    /**
     * Отправляет уведомление об ошибке
     */
    @Transactional
    public void sendErrorNotification(T session, String errorMessage) {
        P progress = buildProgressDto(session);
        setUpdateType(progress, "ERROR");
        setMessage(progress, errorMessage);

        Long operationId = getOperationId(session);
        sendWebSocketUpdate(operationId, progress);

        // Очищаем кеш
        cleanupCache(getSessionId(session));

        log.error("Отправлено уведомление об ошибке для сессии {}: {}",
                getSessionId(session), errorMessage);
    }

    /**
     * Проверяет, нужно ли отправлять обновление (throttling)
     */
    protected boolean shouldSendUpdate(Long sessionId) {
        ZonedDateTime lastUpdate = lastUpdateTimes.get(sessionId);
        ZonedDateTime now = ZonedDateTime.now();

        if (lastUpdate == null || Duration.between(lastUpdate, now).getSeconds() >= 1) {
            lastUpdateTimes.put(sessionId, now);
            return true;
        }
        return false;
    }

    /**
     * Обновляет связанную файловую операцию
     */
    protected void updateFileOperation(T session, P progress) {
        FileOperation operation = getFileOperation(session);
        operation.setProcessingProgress(getProgressPercentage(progress));
        
        Integer processedRecords = getProcessedRecords(progress);
        if (processedRecords != null) {
            operation.setProcessedRecords(processedRecords);
        }
        
        Integer totalRecords = getTotalRecords(progress);
        if (totalRecords != null) {
            operation.setTotalRecords(totalRecords);
        }

        fileOperationRepository.saveAndFlush(operation);
    }

    /**
     * Очищает кеш для сессии
     */
    protected void cleanupCache(Long sessionId) {
        lastUpdateTimes.remove(sessionId);
    }

    // Абстрактные методы для реализации в дочерних классах
    
    /**
     * Создает DTO прогресса из сессии
     */
    protected abstract P buildProgressDto(T session);

    /**
     * Отправляет WebSocket обновление
     */
    protected abstract void sendWebSocketUpdate(Long operationId, P progress);

    /**
     * Строит сообщение о завершении операции
     */
    protected abstract String buildCompletionMessage(T session);

    // Геттеры для извлечения данных из типизированных объектов
    
    protected abstract Long getSessionId(T session);
    protected abstract Long getOperationId(T session);
    protected abstract FileOperation getFileOperation(T session);
    
    protected abstract Integer getProgressPercentage(P progress);
    protected abstract Integer getProcessedRecords(P progress);
    protected abstract Integer getTotalRecords(P progress);
    
    // Сеттеры для установки данных в DTO
    
    protected abstract void setUpdateType(P progress, String updateType);
    protected abstract void setIsCompleted(P progress, boolean isCompleted);
    protected abstract void setMessage(P progress, String message);
}