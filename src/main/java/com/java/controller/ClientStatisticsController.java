package com.java.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.constants.UrlConstants;
import com.java.dto.StatisticsComparisonDto;
import com.java.dto.StatisticsRequestDto;
import com.java.model.entity.ExportSession;
import com.java.repository.ExportSessionRepository;
import com.java.repository.ExportStatisticsRepository;
import com.java.repository.ExportTemplateRepository;
import com.java.service.client.ClientService;
import com.java.service.exports.ExportStatisticsWriterService;
import com.java.service.statistics.ExportStatisticsService;
import com.java.service.statistics.StatisticsSettingsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


import java.util.List;
import java.util.Map;

/**
 * Контроллер для работы со статистикой клиента
 */
@Controller
@RequestMapping("/clients/{clientId}")
@RequiredArgsConstructor
@Slf4j
public class ClientStatisticsController {

    private final ClientService clientService;
    private final ExportStatisticsService statisticsService;
    private final StatisticsSettingsService settingsService;
    private final ExportSessionRepository sessionRepository;
    private final ExportTemplateRepository templateRepository;
    private final ExportStatisticsRepository statisticsRepository;
    private final ExportStatisticsWriterService writerService;

    /**
     * Страница статистики клиента
     */
    @GetMapping("/statistics")
    public String showClientStatistics(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to show statistics page for client id: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("clientId", clientId);
                    
                    // Получаем последние экспорты клиента
                    Page<ExportSession> recentExports = sessionRepository.findByClientId(
                            clientId,
                            PageRequest.of(0, settingsService.getMaxOperations(),
                                    Sort.by(Sort.Direction.DESC, "startedAt"))
                    );

                    // Получаем шаблоны клиента с включенной статистикой
                    var clientEntity = new com.java.model.Client();
                    clientEntity.setId(clientId);
                    var templates = templateRepository.findByClientAndIsActiveTrue(clientEntity).stream()
                            .filter(template -> Boolean.TRUE.equals(template.getEnableStatistics()))
                            .toList();

                    model.addAttribute("recentExports", recentExports.getContent());
                    model.addAttribute("templates", templates);
                    model.addAttribute("maxOperations", settingsService.getMaxOperations());
                    model.addAttribute("warningPercentage", settingsService.getWarningPercentage());
                    model.addAttribute("criticalPercentage", settingsService.getCriticalPercentage());

                    return "clients/statistics";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", clientId);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    /**
     * Страница анализа статистики экспорта для клиента
     */
    @GetMapping("/statistics/export")
    public String showExportStatistics(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to show export statistics page for client id: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("clientId", clientId);
                    
                    // Получаем последние экспорты клиента
                    Page<ExportSession> recentExports = sessionRepository.findByClientId(
                            clientId,
                            PageRequest.of(0, settingsService.getMaxOperations(),
                                    Sort.by(Sort.Direction.DESC, "startedAt"))
                    );

                    // Получаем шаблоны клиента с включенной статистикой
                    var clientEntity = new com.java.model.Client();
                    clientEntity.setId(clientId);
                    var templates = templateRepository.findByClientAndIsActiveTrue(clientEntity).stream()
                            .filter(template -> Boolean.TRUE.equals(template.getEnableStatistics()))
                            .toList();

                    model.addAttribute("recentExports", recentExports.getContent());
                    model.addAttribute("templates", templates);
                    model.addAttribute("maxOperations", settingsService.getMaxOperations());
                    model.addAttribute("warningPercentage", settingsService.getWarningPercentage());
                    model.addAttribute("criticalPercentage", settingsService.getCriticalPercentage());

                    return "statistics/setup";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", clientId);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    /**
     * Анализ статистики экспорта для клиента
     */
    @PostMapping("/statistics/export/analyze")
    public String analyzeExportStatistics(@PathVariable Long clientId,
                                          @ModelAttribute StatisticsRequestDto request,
                                          RedirectAttributes redirectAttributes,
                                          Model model) {
        log.debug("POST request to analyze export statistics for client id: {} with request: {}", clientId, request);

        return clientService.getClientById(clientId)
                .map(client -> {
                    try {
                        // Вычисляем статистику
                        List<StatisticsComparisonDto> comparison = statisticsService.calculateComparison(request);

                        if (comparison.isEmpty()) {
                            redirectAttributes.addFlashAttribute("warningMessage",
                                    "Нет данных для анализа статистики");
                            return "redirect:/clients/" + clientId + "/statistics/export";
                        }

                        // Добавляем данные в модель
                        model.addAttribute("comparison", comparison);
                        model.addAttribute("client", client);
                        model.addAttribute("clientId", clientId);
                        model.addAttribute("request", request);
                        model.addAttribute("warningPercentage",
                                request.getWarningPercentage() != null ? request.getWarningPercentage() : settingsService.getWarningPercentage());
                        model.addAttribute("criticalPercentage",
                                request.getCriticalPercentage() != null ? request.getCriticalPercentage() : settingsService.getCriticalPercentage());

                        return "statistics/results";

                    } catch (Exception e) {
                        log.error("Ошибка анализа статистики", e);
                        redirectAttributes.addFlashAttribute("errorMessage",
                                "Ошибка анализа статистики: " + e.getMessage());
                        return "redirect:/clients/" + clientId + "/statistics/export";
                    }
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", clientId);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    /**
     * AJAX endpoint для предварительного просмотра статистики клиента
     */
    @PostMapping("/statistics/export/preview")
    @ResponseBody
    public Map<String, Object> previewExportStatistics(@PathVariable Long clientId,
                                                        @RequestBody StatisticsRequestDto request) {
        log.debug("AJAX request to preview export statistics for client id: {}", clientId);

        try {
            List<StatisticsComparisonDto> comparison = statisticsService.calculateComparison(request);

            // Возвращаем краткую статистику
            int totalGroups = comparison.size();
            int totalOperations = comparison.stream()
                    .mapToInt(c -> c.getOperations().size())
                    .max().orElse(0);

            long criticalChanges = comparison.stream()
                    .flatMap(c -> c.getOperations().stream())
                    .flatMap(op -> op.getMetrics().values().stream())
                    .filter(metric -> metric.getAlertLevel() == StatisticsComparisonDto.AlertLevel.CRITICAL)
                    .count();

            return Map.of(
                    "success", true,
                    "totalGroups", totalGroups,
                    "totalOperations", totalOperations,
                    "criticalChanges", criticalChanges,
                    "hasData", !comparison.isEmpty()
            );

        } catch (Exception e) {
            log.error("Ошибка предварительного просмотра", e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Получает сохранённую статистику для сессии экспорта клиента
     */
    @GetMapping("/statistics/export/session/{sessionId}/saved")
    @ResponseBody
    public Map<String, Object> getSavedExportStatistics(@PathVariable Long clientId,
                                                         @PathVariable Long sessionId) {
        log.debug("GET request to get saved export statistics for client id: {} session id: {}", clientId, sessionId);

        try {
            var statistics = statisticsRepository.findByExportSessionId(sessionId);

            return Map.of(
                    "success", true,
                    "sessionId", sessionId,
                    "clientId", clientId,
                    "count", statistics.size(),
                    "hasStatistics", !statistics.isEmpty(),
                    "message", statistics.isEmpty() ?
                            "Нет сохранённой статистики для данной сессии" :
                            "Найдено " + statistics.size() + " записей статистики"
            );
        } catch (Exception e) {
            log.error("Ошибка получения сохранённой статистики", e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Экспорт результатов статистики в Excel для клиента
     */
    @PostMapping("/statistics/export/excel")
    public void exportStatisticsToExcel(@PathVariable Long clientId,
                                         @RequestParam String statisticsData,
                                         HttpServletResponse response) {
        log.debug("POST request to export statistics to Excel for client id: {}", clientId);

        try {
            // Парсим JSON данные статистики
            ObjectMapper objectMapper = new ObjectMapper();
            List<StatisticsComparisonDto> comparison = objectMapper.readValue(
                    statisticsData, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, StatisticsComparisonDto.class)
            );

            // Настраиваем ответ для скачивания файла
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", 
                    "attachment; filename=\"statistics_client_" + clientId + "_" + 
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx\"");

            // Записываем данные в Excel файл
//            writerService.writeStatisticsToExcel(comparison, response.getOutputStream());

        } catch (Exception e) {
            log.error("Ошибка экспорта статистики в Excel для клиента " + clientId, e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Ошибка экспорта данных: " + e.getMessage());
            } catch (Exception ex) {
                log.error("Ошибка отправки ошибки клиенту", ex);
            }
        }
    }
}