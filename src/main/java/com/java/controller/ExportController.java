package com.java.controller;

import com.java.dto.ExportRequestDto;
import com.java.dto.ExportSessionDto;
import com.java.dto.ExportTemplateDto;
import com.java.model.FileOperation;
import com.java.repository.ExportSessionRepository;
import com.java.repository.FileOperationRepository;
import com.java.service.exports.ExportService;
import com.java.service.exports.ExportTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Контроллер для операций экспорта
 */
@Controller
@RequestMapping("/export")
@RequiredArgsConstructor
@Slf4j
public class ExportController {

    private final ExportTemplateService templateService;
    private final ExportService exportService;
    private final FileOperationRepository fileOperationRepository;
    private final ExportSessionRepository sessionRepository;

    /**
     * Страница запуска экспорта для клиента
     */
    @GetMapping("/client/{clientId}")
    public String showExportPage(@PathVariable Long clientId, Model model) {
        List<ExportTemplateDto> templates = templateService.getClientTemplates(clientId);
        model.addAttribute("templates", templates);
        model.addAttribute("clientId", clientId);
        return "export/start";
    }

    /**
     * Запуск экспорта
     */
    @PostMapping("/client/{clientId}/start")
    public String startExport(@PathVariable Long clientId,
                              @RequestParam Long templateId,
                              @RequestParam String operationIds,
                              RedirectAttributes redirectAttributes) {
        try {
            List<Long> ops = Arrays.stream(operationIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            ExportRequestDto request = ExportRequestDto.builder()
                    .templateId(templateId)
                    .operationIds(ops)
                    .build();

            ExportSessionDto session = exportService.startExport(request, clientId);

            redirectAttributes.addFlashAttribute("successMessage", "Экспорт запущен");
            return "redirect:/export/status/" + session.getFileOperationId();
        } catch (Exception e) {
            log.error("Ошибка запуска экспорта", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка запуска экспорта: " + e.getMessage());
            return "redirect:/export/client/" + clientId;
        }
    }

    /**
     * Статус операции экспорта
     */
    @GetMapping("/status/{operationId}")
    public String showExportStatus(@PathVariable Long operationId,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        FileOperation operation = fileOperationRepository.findById(operationId).orElse(null);
        if (operation == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Операция не найдена");
            return "redirect:/clients";
        }

        sessionRepository.findByFileOperationId(operationId)
                .ifPresent(session -> model.addAttribute("exportSession", session));

        model.addAttribute("operation", operation);
        model.addAttribute("clientId", operation.getClient().getId());
        model.addAttribute("clientName", operation.getClient().getName());
        model.addAttribute("operationTypeDisplay", getOperationTypeDisplay(operation));
        model.addAttribute("statusDisplay", getStatusDisplay(operation));
        model.addAttribute("statusClass", getStatusClass(operation));
        model.addAttribute("formattedStartedAt", formatDateTime(operation.getStartedAt()));
        model.addAttribute("formattedCompletedAt", formatDateTime(operation.getCompletedAt()));
        model.addAttribute("duration", operation.getDuration());

        return "operations/status";
    }

    private String getOperationTypeDisplay(FileOperation operation) {
        switch (operation.getOperationType()) {
            case IMPORT:
                return "Импорт";
            case EXPORT:
                return "Экспорт";
            case PROCESS:
                return "Обработка";
            default:
                return operation.getOperationType().name();
        }
    }

    private String getStatusDisplay(FileOperation operation) {
        switch (operation.getStatus()) {
            case PENDING:
                return "Ожидание";
            case PROCESSING:
                return "В процессе";
            case COMPLETED:
                return "Завершено";
            case FAILED:
                return "Ошибка";
            default:
                return operation.getStatus().name();
        }
    }

    private String getStatusClass(FileOperation operation) {
        switch (operation.getStatus()) {
            case PENDING:
                return "status-pending";
            case PROCESSING:
                return "status-processing";
            case COMPLETED:
                return "status-success";
            case FAILED:
                return "status-error";
            default:
                return "status-unknown";
        }
    }

    private String formatDateTime(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
    }
}