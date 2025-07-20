package com.java.controller;

import com.java.dto.*;
import com.java.mapper.FileMetadataMapper;
import com.java.model.FileOperation;
import com.java.model.entity.ImportSession;
import com.java.service.file.FileAnalyzerService;
import com.java.service.imports.AsyncImportService;
import com.java.service.imports.ImportTemplateService;
import com.java.repository.FileOperationRepository;
import com.java.repository.ImportSessionRepository;
import com.java.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Контроллер для операций импорта
 */
@Controller
@RequestMapping("/import")
@RequiredArgsConstructor
@Slf4j
public class ImportController {

    private final ImportTemplateService templateService;
    private final FileAnalyzerService fileAnalyzerService;
    private final AsyncImportService asyncImportService;
    private final FileOperationRepository fileOperationRepository;
    private final ImportSessionRepository sessionRepository;
    private final PathResolver pathResolver;

    // Временное хранилище для связи файлов с анализом
    private final Map<String, AnalysisSession> analysisSessions = new ConcurrentHashMap<>();

    /**
     * Очистка старых сессий анализа (запускается каждые 30 минут)
     */
    @Scheduled(fixedDelay = 1800000)
    public void cleanupOldSessions() {
        long cutoff = System.currentTimeMillis() - 3600000; // 1 час
        analysisSessions.entrySet().removeIf(entry ->
                entry.getValue().getCreatedAt() < cutoff
        );
        log.debug("Очищено старых сессий анализа: {}", analysisSessions.size());
    }

    /**
     * Загрузка файлов для импорта
     */
    @PostMapping("/{clientId}/upload")
    public String uploadFiles(@PathVariable Long clientId,
                              @RequestParam("files") MultipartFile[] files,
                              RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на загрузку {} файлов для клиента ID: {}", files.length, clientId);

        try {
            // Создаем сессию анализа
            String sessionId = UUID.randomUUID().toString();
            AnalysisSession session = new AnalysisSession();
            session.setClientId(clientId);

            // Сохраняем файлы и анализируем их
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    Path savedPath = pathResolver.saveToTempFile(file, "import_" + clientId);
                    var metadata = fileAnalyzerService.analyzeFile(file);

                    FileInfo fileInfo = new FileInfo();
                    fileInfo.setOriginalFile(file);
                    fileInfo.setSavedPath(savedPath);
                    fileInfo.setMetadata(metadata);
                    fileInfo.setAnalysisResult(FileMetadataMapper.toAnalysisDto(metadata));

                    session.getFiles().add(fileInfo);
                }
            }

            analysisSessions.put(sessionId, session);

            // Перенаправляем на страницу анализа
            return "redirect:/import/" + clientId + "/analyze?session=" + sessionId;

        } catch (Exception e) {
            log.error("Ошибка загрузки файлов", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка загрузки файлов: " + e.getMessage());
            return "redirect:/clients/" + clientId;
        }
    }

    /**
     * Страница анализа загруженных файлов
     */
    @GetMapping("/{clientId}/analyze")
    public String showAnalysis(@PathVariable Long clientId,
                               @RequestParam("session") String sessionId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на анализ файлов для клиента ID: {}", clientId);

        AnalysisSession session = analysisSessions.get(sessionId);
        if (session == null || !session.getClientId().equals(clientId)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Сессия анализа не найдена");
            return "redirect:/clients/" + clientId;
        }

        // Получаем доступные шаблоны
        var templates = templateService.getClientTemplates(clientId);

        model.addAttribute("sessionId", sessionId);
        model.addAttribute("files", session.getFiles());
        model.addAttribute("templates", templates);
        model.addAttribute("clientId", clientId);

        return "import/analyze";
    }

    /**
     * Запуск импорта файлов
     */
    @PostMapping("/{clientId}/start")
    public String startImport(@PathVariable Long clientId,
                              @RequestParam("sessionId") String sessionId,
                              @RequestParam("templateId") Long templateId,
                              @RequestParam(value = "validateOnly", defaultValue = "false") boolean validateOnly,
                              @RequestParam(value = "asyncMode", defaultValue = "true") boolean asyncMode,
                              RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на запуск импорта для клиента ID: {}", clientId);

        try {
            AnalysisSession session = analysisSessions.get(sessionId);
            if (session == null) {
                throw new IllegalStateException("Сессия анализа не найдена");
            }

            List<Long> operationIds = new ArrayList<>();

            // Запускаем импорт для каждого файла
            for (FileInfo fileInfo : session.getFiles()) {
                ImportRequestDto request = ImportRequestDto.builder()
                        .file(fileInfo.getOriginalFile())
                        .savedFilePath(fileInfo.getSavedPath())
                        .templateId(templateId)
                        .validateOnly(validateOnly)
                        .asyncMode(asyncMode)
                        .build();

                // Запускаем асинхронный импорт
                CompletableFuture<ImportSession> future = asyncImportService.startImport(request, clientId);

                // Получаем сессию для отображения прогресса
                var importSession = future.get(); // Ждем создания сессии
                operationIds.add(importSession.getFileOperation().getId());
            }

            // Очищаем сессию анализа
            analysisSessions.remove(sessionId);

            // Если один файл - перенаправляем на его статус
            if (operationIds.size() == 1) {
                return "redirect:/import/status/" + operationIds.get(0);
            }

            // Если несколько - на список операций клиента
            redirectAttributes.addFlashAttribute("successMessage",
                    "Запущен импорт " + operationIds.size() + " файлов");
            return "redirect:/clients/" + clientId + "#operations";

        } catch (Exception e) {
            log.error("Ошибка запуска импорта", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка запуска импорта: " + e.getMessage());
            return "redirect:/clients/" + clientId;
        }
    }

    /**
     * Отображение статуса импорта
     */
    @GetMapping("/status/{operationId}")
    public String showImportStatus(@PathVariable Long operationId,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на просмотр статуса операции ID: {}", operationId);

        FileOperation operation = fileOperationRepository.findById(operationId)
                .orElse(null);

        if (operation == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Операция не найдена");
            return "redirect:/clients";
        }

        // Находим сессию импорта
        sessionRepository.findByFileOperationId(operationId)
                .ifPresent(session -> model.addAttribute("importSession", session));

        model.addAttribute("operation", operation);
        model.addAttribute("clientId", operation.getClient().getId());
        model.addAttribute("clientName", operation.getClient().getName());

        // Добавляем вспомогательные атрибуты для отображения
        model.addAttribute("operationTypeDisplay", getOperationTypeDisplay(operation));
        model.addAttribute("statusDisplay", getStatusDisplay(operation));
        model.addAttribute("statusClass", getStatusClass(operation));
        model.addAttribute("formattedStartedAt", formatDateTime(operation.getStartedAt()));
        model.addAttribute("formattedCompletedAt", formatDateTime(operation.getCompletedAt()));
        model.addAttribute("duration", operation.getDuration());

        return "operations/status";
    }

    /**
     * API endpoint для отмены импорта
     */
    @PostMapping("/api/cancel/{operationId}")
    @ResponseBody
    public ImportCancelResponse cancelImport(@PathVariable Long operationId) {
        log.debug("POST запрос на отмену импорта для операции ID: {}", operationId);

        try {
            var sessionOpt = sessionRepository.findByFileOperationId(operationId);

            if (sessionOpt.isEmpty()) {
                return new ImportCancelResponse(false, "Сессия импорта не найдена");
            }

            boolean cancelled = asyncImportService.cancelImport(sessionOpt.get().getId());

            if (cancelled) {
                return new ImportCancelResponse(true, "Импорт отменен");
            } else {
                return new ImportCancelResponse(false, "Невозможно отменить импорт в текущем статусе");
            }

        } catch (Exception e) {
            log.error("Ошибка отмены импорта", e);
            return new ImportCancelResponse(false, e.getMessage());
        }
    }

    // Вспомогательные методы
    private String getOperationTypeDisplay(FileOperation operation) {
        switch (operation.getOperationType()) {
            case IMPORT: return "Импорт";
            case EXPORT: return "Экспорт";
            case PROCESS: return "Обработка";
            default: return operation.getOperationType().name();
        }
    }

    private String getStatusDisplay(FileOperation operation) {
        switch (operation.getStatus()) {
            case PENDING: return "Ожидание";
            case PROCESSING: return "В процессе";
            case COMPLETED: return "Завершено";
            case FAILED: return "Ошибка";
            default: return operation.getStatus().name();
        }
    }

    private String getStatusClass(FileOperation operation) {
        switch (operation.getStatus()) {
            case PENDING: return "status-pending";
            case PROCESSING: return "status-processing";
            case COMPLETED: return "status-success";
            case FAILED: return "status-error";
            default: return "status-unknown";
        }
    }

    private String formatDateTime(java.time.ZonedDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
    }

    // Внутренние классы
    @lombok.Data
    private static class AnalysisSession {
        private Long clientId;
        private List<FileInfo> files = new ArrayList<>();
        private long createdAt = System.currentTimeMillis();
    }

    @lombok.Data
    private static class FileInfo {
        private MultipartFile originalFile;
        private Path savedPath;
        private com.java.model.entity.FileMetadata metadata;
        private FileAnalysisResultDto analysisResult;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ImportCancelResponse {
        private boolean success;
        private String message;
    }
}