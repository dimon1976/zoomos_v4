// src/main/java/com/java/controller/ExportStatisticsController.java
package com.java.controller;

import com.java.dto.StatisticsComparisonDto;
import com.java.dto.StatisticsRequestDto;
import com.java.model.entity.ExportSession;
import com.java.repository.ExportSessionRepository;
import com.java.repository.ExportStatisticsRepository;
import com.java.repository.ExportTemplateRepository;
import com.java.service.exports.ExportStatisticsWriterService;
import com.java.service.statistics.ExportStatisticsService;
import com.java.service.statistics.StatisticsSettingsService;
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

        // Получаем последние экспорты клиента
        Page<ExportSession> recentExports = sessionRepository.findByClientId(
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
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        log.debug("POST запрос на анализ статистики: {}", request);

        try {
            // Вычисляем статистику
            List<StatisticsComparisonDto> comparison = statisticsService.calculateComparison(request);

            if (comparison.isEmpty()) {
                redirectAttributes.addFlashAttribute("warningMessage",
                        "Нет данных для анализа статистики");
                return "redirect:/statistics/client/" + request.getTemplateId(); // Временно
            }

            // Добавляем данные в модель
            model.addAttribute("comparison", comparison);
            model.addAttribute("request", request);
            model.addAttribute("warningPercentage",
                    request.getWarningPercentage() != null ? request.getWarningPercentage() : settingsService.getWarningPercentage());
            model.addAttribute("criticalPercentage",
                    request.getCriticalPercentage() != null ? request.getCriticalPercentage() : settingsService.getCriticalPercentage());

            return "statistics/results";

        } catch (Exception e) {
            log.error("Ошибка анализа статистики", e);

            // Находим clientId через templateId
            Long clientId = null;
            try {
                var template = templateRepository.findById(request.getTemplateId());
                if (template.isPresent()) {
                    clientId = template.get().getClient().getId();
                }
            } catch (Exception ex) {
                log.error("Не удалось найти клиента по templateId", ex);
            }

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

}