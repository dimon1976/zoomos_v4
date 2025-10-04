// src/main/java/com/java/controller/ExportStatisticsController.java
package com.java.controller;

import com.java.dto.StatisticsComparisonDto;
import com.java.dto.StatisticsRequestDto;
import com.java.model.entity.ExportSession;
import com.java.repository.ExportSessionRepository;
import com.java.repository.ExportStatisticsRepository;
import com.java.repository.ExportTemplateRepository;
import com.java.service.statistics.ExportStatisticsService;
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
    private final StatisticsSettingsService settingsService;
    private final ExportSessionRepository sessionRepository;
    private final ExportTemplateRepository templateRepository;
    private final ExportStatisticsRepository statisticsRepository;

    /**
     * Страница выбора операций для анализа статистики
     */
    @GetMapping("/client/{clientId}")
    public String showStatisticsSetup(@PathVariable Long clientId, Model model) {
        log.debug("GET запрос на настройку статистики для клиента ID: {}", clientId);

        // Получаем последние экспорты клиента с загруженными данными fileOperation
        Page<ExportSession> recentExports = sessionRepository.findByClientIdWithTemplate(
                clientId,
                PageRequest.of(0, settingsService.getMaxOperations(),
                        Sort.by(Sort.Direction.DESC, "startedAt"))
        );

        // Получаем шаблоны клиента с включенной статистикой
        var client = new com.java.model.Client();
        client.setId(clientId);
        var templates = templateRepository.findByClientAndIsActiveTrue(client).stream()
                .filter(template -> Boolean.TRUE.equals(template.getEnableStatistics()))
                .toList();

        model.addAttribute("clientId", clientId);
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
                                    RedirectAttributes redirectAttributes,
                                    Long clientId,
                                    Model model) {
        log.debug("POST запрос на анализ статистики: {} с фильтром: {}={}",
                request, filterField, filterValue);

        try {
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
                return "redirect:/statistics/client/" + request.getTemplateId();
            }

            // Добавляем данные в модель
            model.addAttribute("comparison", comparison);
            model.addAttribute("clientId", clientId);
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
        log.debug("API запрос на анализ статистики: {}", request);
        return statisticsService.calculateComparison(request);
    }

    /**
     * Страница настроек статистики (глобальные настройки)
     */
    @GetMapping("/settings")
    public String showSettings(Model model) {
        log.debug("GET запрос на страницу настроек статистики");

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
        log.debug("POST запрос на обновление настроек статистики: {}", params);

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
        log.debug("GET запрос на получение сохранённой статистики для сессии ID: {}", sessionId);

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
        log.debug("Диагностика данных для клиента ID: {}", clientId);

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
        log.debug("AJAX запрос на предварительный просмотр статистики");

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

        log.debug("GET запрос на получение значений фильтров для шаблона {} и сессий {}",
                templateId, sessionIds);

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

            log.debug("Найдено {} полей фильтрации с значениями", result.size());
            return result;

        } catch (Exception e) {
            log.error("Ошибка получения значений фильтров", e);
            return Map.of();
        }
    }


}