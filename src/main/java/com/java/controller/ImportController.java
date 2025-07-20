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
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.concurrent.CompletableFuture;

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

    /**
     * Анализ файла и выбор шаблона
     */
    @PostMapping("/{clientId}/analyze")
    public String analyzeFile(@PathVariable Long clientId,
                              @RequestParam("file") MultipartFile file,
                              Model model,
                              HttpSession httpSession) {
        log.debug("POST запрос на анализ файла для клиента ID: {}", clientId);

        try {
            // Анализируем файл
            var metadata = fileAnalyzerService.analyzeFile(file);
            var analysisResult = FileMetadataMapper.toAnalysisDto(metadata);

            // Получаем доступные шаблоны
            var templates = templateService.getClientTemplates(clientId);

            // Сохраняем в сессии для следующего шага
            httpSession.setAttribute("fileMetadata", metadata);
            httpSession.setAttribute("originalFile", file);

            model.addAttribute("analysis", analysisResult);
            model.addAttribute("templates", templates);
            model.addAttribute("clientId", clientId);

            return "import/analyze";

        } catch (Exception e) {
            log.error("Ошибка анализа файла", e);
            model.addAttribute("errorMessage", "Ошибка анализа файла: " + e.getMessage());
            return "clients/details";
        }
    }

    /**
     * Запуск импорта
     */
    @PostMapping("/{clientId}/start")
    public String startImport(@PathVariable Long clientId,
                              @ModelAttribute ImportRequestDto request,
                              HttpSession httpSession,
                              RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на запуск импорта для клиента ID: {}", clientId);

        try {
            // Получаем файл из сессии
            MultipartFile file = (MultipartFile) httpSession.getAttribute("originalFile");
            if (file == null) {
                throw new IllegalStateException("Файл не найден в сессии");
            }

            request.setFile(file);

            // Запускаем асинхронный импорт
            CompletableFuture<ImportSession> future = asyncImportService.startImport(request, clientId);

            // Получаем сессию для отображения прогресса
            var session = future.get(); // Ждем создания сессии

            // Очищаем сессию
            httpSession.removeAttribute("fileMetadata");
            httpSession.removeAttribute("originalFile");

            // Перенаправляем на страницу прогресса
            return "redirect:/import/status/" + session.getFileOperation().getId();

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

        // Находим операцию
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
            // Находим сессию импорта
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

    // Внутренние классы для ответов

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ImportCancelResponse {
        private boolean success;
        private String message;
    }
}
