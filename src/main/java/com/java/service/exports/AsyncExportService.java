package com.java.service.exports;

import com.java.dto.ExportRequestDto;
import com.java.model.entity.ExportSession;
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

    /**
     * Запускает асинхронный экспорт
     */
    @Async("exportTaskExecutor")
    @Transactional
    public CompletableFuture<ExportSession> startAsyncExport(
            ExportSession session,
            ExportRequestDto request) {

        log.info("Запуск асинхронного экспорта для сессии ID: {}", session.getId());

        try {
            // Выполняем обработку
            processorService.processExport(session, request);

            return CompletableFuture.completedFuture(session);

        } catch (Exception e) {
            log.error("Ошибка асинхронного экспорта", e);
            return CompletableFuture.failedFuture(e);
        }
    }
}