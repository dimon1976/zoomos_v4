package com.java.service.exports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.ExportRequestDto;
import com.java.dto.ExportSessionDto;
import com.java.mapper.ExportSessionMapper;
import com.java.model.Client;
import com.java.model.FileOperation;
import com.java.model.entity.ExportSession;
import com.java.model.entity.ExportTemplate;
import com.java.repository.ClientRepository;
import com.java.repository.ExportSessionRepository;
import com.java.repository.ExportTemplateRepository;
import com.java.repository.FileOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Главный сервис для управления экспортом
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExportService {

    private final ExportTemplateRepository templateRepository;
    private final ExportSessionRepository sessionRepository;
    private final FileOperationRepository fileOperationRepository;
    private final ClientRepository clientRepository;
    private final ExportProcessorService processorService;
    private final AsyncExportService asyncExportService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${export.async.threshold-rows:10000}")
    private int asyncThresholdRows;

    /**
     * Запускает экспорт данных
     */
    @Transactional
    public ExportSessionDto startExport(ExportRequestDto request, Long clientId) {
        log.info("Запуск экспорта для клиента ID: {}", clientId);

        // Получаем клиента
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));

        // Получаем шаблон
        ExportTemplate template = templateRepository.findByIdWithFieldsAndFilters(request.getTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

        // Проверяем, что шаблон принадлежит клиенту
        if (!template.getClient().getId().equals(clientId)) {
            throw new IllegalArgumentException("Шаблон не принадлежит клиенту");
        }

        // Создаем FileOperation
        FileOperation fileOperation = FileOperation.builder()
                .client(client)
                .operationType(FileOperation.OperationType.EXPORT)
                .fileName("export_" + template.getName() + "_" + System.currentTimeMillis())
                .fileType(template.getFileFormat())
                .status(FileOperation.OperationStatus.PROCESSING)
                .createdBy("system") // TODO: получать из контекста безопасности
                .build();

        fileOperation = fileOperationRepository.save(fileOperation);

        // Создаем сессию экспорта
        ExportSession session = ExportSession.builder()
                .fileOperation(fileOperation)
                .template(template)
                .sourceOperationIds(objectMapper.valueToTree(request.getOperationIds()).toString())
                .dateFilterFrom(request.getDateFrom())
                .dateFilterTo(request.getDateTo())
                .appliedFilters(request.getAdditionalFilters() != null ?
                        objectMapper.valueToTree(request.getAdditionalFilters()).toString() : null)
                .build();

        session = sessionRepository.save(session);

        // Определяем режим обработки
        if (request.getAsyncMode() && shouldProcessAsync(request)) {
            // Асинхронная обработка после коммита транзакции
            log.info("Запуск асинхронного экспорта");
//            CompletableFuture<ExportSession> future = asyncExportService.startAsyncExport(session, request);
            ExportSession finalSession = session;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncExportService.startAsyncExport(finalSession, request);
                }
            });

            // Возвращаем DTO сразу, не дожидаясь завершения
            return ExportSessionMapper.toDto(session);
        } else {
            // Синхронная обработка
            log.info("Запуск синхронного экспорта");
            processorService.processExport(session, request);

            // Перезагружаем сессию для получения обновленных данных
            session = sessionRepository.findById(session.getId()).orElse(session);
            return ExportSessionMapper.toDto(session);
        }
    }

    /**
     * Определяет, нужна ли асинхронная обработка
     */
    private boolean shouldProcessAsync(ExportRequestDto request) {
        // Можно добавить логику определения на основе предполагаемого объема данных
        // Пока используем простое правило - всегда асинхронно
        return true;
    }

    /**
     * Получает информацию о сессии экспорта
     */
    @Transactional(readOnly = true)
    public ExportSessionDto getExportSession(Long sessionId) {
        ExportSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Сессия экспорта не найдена"));

        return ExportSessionMapper.toDto(session);
    }

    /**
     * Получает историю экспортов клиента
     */
    @Transactional(readOnly = true)
    public Page<ExportSessionDto> getClientExportHistory(Long clientId, Pageable pageable) {
        Page<ExportSession> sessions = sessionRepository.findByClientId(clientId, pageable);
        return sessions.map(ExportSessionMapper::toDto);
    }

    /**
     * Отменяет экспорт
     */
    @Transactional
    public boolean cancelExport(Long sessionId) {
        log.info("Запрос на отмену экспорта для сессии ID: {}", sessionId);

        return sessionRepository.findById(sessionId)
                .map(session -> {
                    if (session.getStatus().name().equals("PROCESSING") ||
                            session.getStatus().name().equals("INITIALIZING")) {

                        // Отправляем сигнал отмены процессору
                        processorService.cancelExport(sessionId);
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }
}