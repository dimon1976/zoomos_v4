package com.java.controller;

import com.java.dto.Breadcrumb;
import com.java.dto.ExportRequestDto;
import com.java.dto.ExportSessionDto;
import com.java.dto.ExportTemplateDto;
import com.java.model.Client;
import com.java.model.FileOperation;
import com.java.repository.ClientRepository;
import com.java.repository.ExportSessionRepository;
import com.java.repository.FileOperationRepository;
import com.java.service.EntityFieldService;
import com.java.service.exports.ExportService;
import com.java.service.exports.ExportTemplateService;
import com.java.util.ControllerUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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
    private final EntityFieldService fieldService;
    private final ControllerUtils controllerUtils;
    private final ClientRepository clientRepository;

    /**
     * Страница запуска экспорта для клиента
     */
    @GetMapping("/client/{clientId}")
    public String showExportPage(@PathVariable Long clientId, Model model) {
        // Получаем клиента для breadcrumbs
        var client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));

        List<ExportTemplateDto> templates = templateService.getClientTemplates(clientId);

        // Получаем все импорты (включая ошибочные для визуальной индикации)
        List<FileOperation> recentOperations = fileOperationRepository
                .findRecentImportOperations(client, PageRequest.of(0, 20));

        // Создаём breadcrumbs
        List<Breadcrumb> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(Breadcrumb.builder().label("Главная").url("/").build());
        breadcrumbs.add(Breadcrumb.builder().label(client.getName()).url("/clients/" + clientId).build());
        breadcrumbs.add(Breadcrumb.builder().label("Экспорт").url(null).build());

        model.addAttribute("breadcrumbs", breadcrumbs);
        model.addAttribute("templates", templates);
        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", client.getName());
        model.addAttribute("recentOperations", recentOperations);
        model.addAttribute("request", new ExportRequestDto());
        return "export/start";
    }

    /**
     * Возвращает список полей для шаблона
     */
    @GetMapping("/template/{templateId}/fields")
    @ResponseBody
    public List<String> getTemplateFields(@PathVariable Long templateId) {
        return templateService.getTemplate(templateId)
                .map(t -> fieldService.getFields(t.getEntityType()))
                .orElseGet(ArrayList::new);
    }

    /**
     * Запуск экспорта
     */
    @PostMapping("/client/{clientId}/start")
    public String startExport(@PathVariable Long clientId,
                              @ModelAttribute ExportRequestDto request,
                              @RequestParam(name = "selectedOperationIds", required = false) List<Long> selectedOperations,
                              @RequestParam(value = "exportAll", defaultValue = "false") boolean exportAll,
                              @RequestParam(name = "reportDateFrom", required = false) LocalDate reportDateFrom,
                              RedirectAttributes redirectAttributes) {
        try {
            List<Long> ops = new ArrayList<>();
            if (!exportAll && selectedOperations != null) {
                ops.addAll(selectedOperations);
            }

            // Even when exporting the entire table, keep an empty list instead of null
            // to avoid null value issues in subsequent processing
            request.setOperationIds(ops);

            // Пересчёт количества дней из выбранной даты
            if (reportDateFrom != null) {
                long daysDiff = ChronoUnit.DAYS.between(reportDateFrom, LocalDate.now());
                request.setMaxReportAgeDays((int) daysDiff);
            }

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
        FileOperation operation = fileOperationRepository.findByIdWithClient(operationId).orElse(null);
        if (operation == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Операция не найдена");
            return "redirect:/clients";
        }

        sessionRepository.findByFileOperationId(operationId)
                .ifPresent(session -> {
                    model.addAttribute("exportSession", session);
                    model.addAttribute("templateName", session.getTemplate().getName());
                });

        // Создаём breadcrumbs
        Long clientId = operation.getClient().getId();
        String clientName = operation.getClient().getName();
        List<Breadcrumb> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(Breadcrumb.builder().label("Главная").url("/").build());
        breadcrumbs.add(Breadcrumb.builder().label(clientName).url("/clients/" + clientId).build());
        breadcrumbs.add(Breadcrumb.builder().label("Экспорт").url("/export/client/" + clientId).build());
        breadcrumbs.add(Breadcrumb.builder().label("Статус #" + operationId).url(null).build());

        model.addAttribute("breadcrumbs", breadcrumbs);
        model.addAttribute("operation", operation);
        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", clientName);
        model.addAttribute("operationTypeDisplay", controllerUtils.getOperationTypeDisplay(operation.getOperationType()));
        model.addAttribute("statusDisplay", controllerUtils.getStatusDisplay(operation.getStatus()));
        model.addAttribute("statusClass", controllerUtils.getStatusClass(operation.getStatus()));
        model.addAttribute("formattedStartedAt", controllerUtils.formatDateTime(operation.getStartedAt()));
        model.addAttribute("formattedCompletedAt", controllerUtils.formatDateTime(operation.getCompletedAt()));
        model.addAttribute("duration", operation.getDuration());

        return "operations/status";
    }

}