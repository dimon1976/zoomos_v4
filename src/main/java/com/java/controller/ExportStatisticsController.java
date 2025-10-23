// src/main/java/com/java/controller/ExportStatisticsController.java
package com.java.controller;

import com.java.dto.Breadcrumb;
import com.java.dto.StatisticsComparisonDto;
import com.java.dto.StatisticsHistoryDto;
import com.java.dto.StatisticsRequestDto;
import com.java.model.entity.ExportSession;
import com.java.repository.ClientRepository;
import com.java.repository.ExportSessionRepository;
import com.java.repository.ExportStatisticsRepository;
import com.java.repository.ExportTemplateRepository;
import com.java.service.statistics.ExportStatisticsService;
import com.java.service.statistics.HistoricalStatisticsService;
import com.java.service.statistics.StatisticsSettingsService;
import com.java.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Контроллер для работы со статистикой экспорта
 */
@Controller
@RequestMapping("/statistics")
@RequiredArgsConstructor
@Slf4j
public class ExportStatisticsController {

    private final ExportStatisticsService statisticsService;
    private final HistoricalStatisticsService historicalStatisticsService;
    private final StatisticsSettingsService settingsService;
    private final ExportSessionRepository sessionRepository;
    private final ExportTemplateRepository templateRepository;
    private final ExportStatisticsRepository statisticsRepository;
    private final ClientRepository clientRepository;

    /**
     * Страница выбора операций для анализа статистики
     */
    @GetMapping("/client/{clientId}")
    public String showStatisticsSetup(@PathVariable Long clientId,
                                      @RequestParam(required = false) Long templateId,
                                      Model model) {

        // Получаем клиента для breadcrumbs
        var client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));

        // Получаем последние экспорты клиента (с фильтрацией по шаблону если указан)
        Page<ExportSession> recentExports;
        if (templateId != null) {
            var template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));
            recentExports = sessionRepository.findByTemplateWithTemplate(
                    template,
                    PageRequest.of(0, settingsService.getMaxOperations(),
                            Sort.by(Sort.Direction.DESC, "startedAt"))
            );
        } else {
            recentExports = sessionRepository.findByClientIdWithTemplate(
                    clientId,
                    PageRequest.of(0, settingsService.getMaxOperations(),
                            Sort.by(Sort.Direction.DESC, "startedAt"))
            );
        }

        // Получаем шаблоны клиента с включенной статистикой
        var templates = templateRepository.findByClientAndIsActiveTrue(client).stream()
                .filter(template -> Boolean.TRUE.equals(template.getEnableStatistics()))
                .toList();

        // Создаём breadcrumbs
        List<Breadcrumb> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(Breadcrumb.builder().label("Главная").url("/").build());
        breadcrumbs.add(Breadcrumb.builder().label(client.getName()).url("/clients/" + clientId).build());
        breadcrumbs.add(Breadcrumb.builder().label("Статистика").url(null).build());

        model.addAttribute("breadcrumbs", breadcrumbs);
        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", client.getName());
        model.addAttribute("selectedTemplateId", templateId);
        model.addAttribute("recentExports", recentExports.getContent());
        model.addAttribute("templates", templates);
        model.addAttribute("maxOperations", settingsService.getMaxOperations());
        model.addAttribute("warningPercentage", settingsService.getWarningPercentage());
        model.addAttribute("criticalPercentage", settingsService.getCriticalPercentage());

        return "statistics/setup";
    }

    /**
     * Запуск анализа статистики
     */
    @PostMapping("/analyze")
    public String analyzeStatistics(@ModelAttribute StatisticsRequestDto request,
                                    @RequestParam(required = false) String filterField,
                                    @RequestParam(required = false) String filterValue,
                                    @RequestParam Long clientId,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {

        try {
            // Получаем клиента для breadcrumbs
            var client = clientRepository.findById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));

            // Валидация параметров фильтра
            if ((filterField != null && filterField.trim().isEmpty()) ||
                (filterValue != null && filterValue.trim().isEmpty())) {
                throw new IllegalArgumentException("Параметры фильтра не могут быть пустыми");
            }

            // Проверка: если указан filterField, должен быть и filterValue (и наоборот)
            if ((filterField != null && filterValue == null) ||
                (filterField == null && filterValue != null)) {
                throw new IllegalArgumentException("Оба параметра фильтра должны быть указаны одновременно");
            }

            // Вычисляем статистику с учетом фильтра
            List<StatisticsComparisonDto> comparison = statisticsService.calculateComparison(
                    request, filterField, filterValue);

            if (comparison.isEmpty()) {
                redirectAttributes.addFlashAttribute("warningMessage",
                        "Нет данных для анализа статистики");
                return "redirect:/statistics/client/" + clientId;
            }

            // Создаём breadcrumbs
            List<Breadcrumb> breadcrumbs = new ArrayList<>();
            breadcrumbs.add(Breadcrumb.builder().label("Главная").url("/").build());
            breadcrumbs.add(Breadcrumb.builder().label(client.getName()).url("/clients/" + clientId).build());
            breadcrumbs.add(Breadcrumb.builder().label("Статистика").url("/statistics/client/" + clientId).build());
            breadcrumbs.add(Breadcrumb.builder().label("Результаты анализа").url(null).build());

            // Добавляем данные в модель
            model.addAttribute("breadcrumbs", breadcrumbs);
            model.addAttribute("comparison", comparison);
            model.addAttribute("clientId", clientId);
            model.addAttribute("clientName", client.getName());
            model.addAttribute("request", request);
            model.addAttribute("filterField", filterField);
            model.addAttribute("filterValue", filterValue);
            model.addAttribute("warningPercentage",
                    request.getWarningPercentage() != null ? request.getWarningPercentage() : settingsService.getWarningPercentage());
            model.addAttribute("criticalPercentage",
                    request.getCriticalPercentage() != null ? request.getCriticalPercentage() : settingsService.getCriticalPercentage());

            return "statistics/results";

        } catch (Exception e) {
            log.error("Ошибка анализа статистики", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка анализа статистики: " + e.getMessage());

            if (clientId != null) {
                return "redirect:/statistics/client/" + clientId;
            } else {
                return "redirect:/clients";
            }
        }
    }

    /**
     * API endpoint для получения данных статистики в JSON формате
     */
    @PostMapping("/api/analyze")
    @ResponseBody
    public List<StatisticsComparisonDto> analyzeStatisticsApi(@RequestBody StatisticsRequestDto request) {
        return statisticsService.calculateComparison(request);
    }

    /**
     * Страница настроек статистики (глобальные настройки)
     */
    @GetMapping("/settings")
    public String showSettings(Model model) {

        Map<String, String> settings = settingsService.getAllSettings();
        model.addAttribute("settings", settings);

        return "statistics/settings";
    }

    /**
     * Обновление настроек статистики
     */
    @PostMapping("/settings/update")
    public String updateSettings(@RequestParam Map<String, String> params,
                                 RedirectAttributes redirectAttributes) {

        try {
            // Обновляем только разрешенные настройки
            String[] allowedSettings = {
                    "deviation_percentage_warning",
                    "deviation_percentage_critical",
                    "statistics_max_operations"
            };

            for (String setting : allowedSettings) {
                if (params.containsKey(setting)) {
                    String value = params.get(setting);

                    // Валидация для числовых настроек
                    if (setting.contains("percentage") || setting.contains("max")) {
                        try {
                            int intValue = Integer.parseInt(value);
                            if (intValue < 0 || intValue > 100) {
                                throw new IllegalArgumentException("Значение должно быть от 0 до 100");
                            }
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Некорректное числовое значение: " + value);
                        }
                    }

                    settingsService.updateSetting(setting, value);
                }
            }

            redirectAttributes.addFlashAttribute("successMessage", "Настройки успешно обновлены");

        } catch (Exception e) {
            log.error("Ошибка обновления настроек", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка обновления настроек: " + e.getMessage());
        }

        return "redirect:/statistics/settings";
    }

    /**
     * Получает сохранённую статистику для сессии экспорта
     */
    @GetMapping("/session/{sessionId}/saved")
    @ResponseBody
    public Map<String, Object> getSavedStatistics(@PathVariable Long sessionId) {

        try {
            var statistics = statisticsRepository.findByExportSessionId(sessionId);

            return Map.of(
                    "success", true,
                    "sessionId", sessionId,
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
     * Диагностический endpoint для проверки данных
     */
    @GetMapping("/debug-data/client/{clientId}")
    @ResponseBody
    public Map<String, Object> debugData(@PathVariable Long clientId) {

        Page<ExportSession> recentExports = sessionRepository.findByClientIdWithTemplate(
                clientId,
                PageRequest.of(0, settingsService.getMaxOperations(),
                        Sort.by(Sort.Direction.DESC, "startedAt"))
        );

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("exportsCount", recentExports.getContent().size());
        
        for (ExportSession export : recentExports.getContent()) {
            if (export.getFileOperation().getId() == 258L) {
                result.put("export258_sessionId", export.getId());
                result.put("export258_sessionStarted", export.getStartedAt());
                result.put("export258_sessionStarted_formatted", 
                    export.getStartedAt().toString());
                result.put("export258_fileOpId", export.getFileOperation().getId());
                result.put("export258_fileOpStarted", export.getFileOperation().getStartedAt());
                result.put("export258_fileOpStarted_formatted", 
                    export.getFileOperation().getStartedAt().toString());
                
                // Добавим системную информацию
                result.put("serverTimeZone", java.util.TimeZone.getDefault().getID());
                result.put("currentServerTime", java.time.Instant.now().toString());
                
                // Проверим разницу во времени
                long timeDifference = export.getStartedAt().toInstant().toEpochMilli() - 
                                    export.getFileOperation().getStartedAt().toInstant().toEpochMilli();
                result.put("timeDifferenceMs", timeDifference);
                
                break;
            }
        }
        
        return result;
    }

    /**
     * AJAX endpoint для предварительного просмотра статистики
     */
    @PostMapping("/preview")
    @ResponseBody
    public Map<String, Object> previewStatistics(@RequestBody StatisticsRequestDto request) {

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
     * API endpoint для получения доступных значений фильтров
     */
    @GetMapping("/filter-values")
    @ResponseBody
    public Map<String, List<String>> getFilterValues(
            @RequestParam Long templateId,
            @RequestParam List<Long> sessionIds) {

        try {
            // Получаем шаблон
            var template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

            // Получаем поля фильтрации из шаблона
            List<String> filterFields = JsonUtils.parseJsonStringList(template.getStatisticsFilterFields());

            if (filterFields.isEmpty()) {
                return Map.of(); // Пустой Map если нет полей фильтрации
            }

            // Для каждого поля получаем уникальные значения
            Map<String, List<String>> result = new java.util.HashMap<>();

            for (String filterField : filterFields) {
                List<String> values = statisticsRepository.findDistinctFilterValues(
                        sessionIds, filterField);
                result.put(filterField, values);
            }

            return result;

        } catch (Exception e) {
            log.error("Ошибка получения значений фильтров", e);
            return Map.of();
        }
    }

    /**
     * API endpoint для получения исторических данных метрики для конкретной группы
     * GET /statistics/history?templateId=1&groupValue=Group1&metricName=price&limit=50
     */
    @GetMapping("/history")
    @ResponseBody
    public StatisticsHistoryDto getMetricHistory(
            @RequestParam Long templateId,
            @RequestParam String groupValue,
            @RequestParam String metricName,
            @RequestParam(required = false) String filterFieldName,
            @RequestParam(required = false) String filterFieldValue,
            @RequestParam(defaultValue = "50") int limit) {

        try {
            return historicalStatisticsService.getHistoryForMetric(
                    templateId, groupValue, metricName, filterFieldName, filterFieldValue, limit);
        } catch (Exception e) {
            log.error("Ошибка получения истории метрики", e);
            throw new RuntimeException("Ошибка получения истории: " + e.getMessage());
        }
    }

    /**
     * API endpoint для получения истории всех групп по одной метрике
     * GET /statistics/history/all-groups?templateId=1&metricName=price&limit=50
     */
    @GetMapping("/history/all-groups")
    @ResponseBody
    public List<StatisticsHistoryDto> getMetricHistoryAllGroups(
            @RequestParam Long templateId,
            @RequestParam String metricName,
            @RequestParam(required = false) String filterFieldName,
            @RequestParam(required = false) String filterFieldValue,
            @RequestParam(defaultValue = "50") int limit) {

        try {
            return historicalStatisticsService.getHistoryForMetricAllGroups(
                    templateId, metricName, filterFieldName, filterFieldValue, limit);
        } catch (Exception e) {
            log.error("Ошибка получения истории всех групп", e);
            throw new RuntimeException("Ошибка получения истории: " + e.getMessage());
        }
    }

    /**
     * API endpoint для получения списка доступных метрик для шаблона
     * GET /statistics/metrics?templateId=1
     */
    @GetMapping("/metrics")
    @ResponseBody
    public List<String> getAvailableMetrics(@RequestParam Long templateId) {
        try {
            return historicalStatisticsService.getAvailableMetrics(templateId);
        } catch (Exception e) {
            log.error("Ошибка получения списка метрик", e);
            return List.of();
        }
    }

    /**
     * API endpoint для получения списка доступных групп для шаблона
     * GET /statistics/groups?templateId=1
     */
    @GetMapping("/groups")
    @ResponseBody
    public List<String> getAvailableGroups(@RequestParam Long templateId) {
        try {
            return historicalStatisticsService.getAvailableGroups(templateId);
        } catch (Exception e) {
            log.error("Ошибка получения списка групп", e);
            return List.of();
        }
    }


}