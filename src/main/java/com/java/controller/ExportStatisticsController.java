package com.java.controller;

import com.java.dto.ExportStatisticsConfigDto;
import com.java.dto.ExportStatisticsDto;
import com.java.dto.ExportTemplateDto;
import com.java.model.entity.ExportSession;
import com.java.repository.ExportSessionRepository;
import com.java.service.EntityFieldService;
import com.java.service.exports.ExportStatisticsService;
import com.java.service.exports.ExportTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Контроллер для работы со статистикой экспорта
 */
@Controller
@RequestMapping("/export/statistics")
@RequiredArgsConstructor
@Slf4j
public class ExportStatisticsController {

    private final ExportStatisticsService statisticsService;
    private final ExportTemplateService templateService;
    private final ExportSessionRepository sessionRepository;
    private final EntityFieldService fieldService;

    /**
     * Страница настройки статистики для шаблона
     */
    @GetMapping("/template/{templateId}/configure")
    public String showConfigPage(@PathVariable Long templateId, Model model) {
        log.debug("Показ страницы настройки статистики для шаблона ID: {}", templateId);

        ExportTemplateDto template = templateService.getTemplate(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

        ExportStatisticsConfigDto config = statisticsService.getStatisticsConfig(templateId)
                .orElseGet(() -> ExportStatisticsConfigDto.builder()
                        .templateId(templateId)
                        .build());

        List<String> availableFields = fieldService.getFields(template.getEntityType());

        model.addAttribute("template", template);
        model.addAttribute("config", config);
        model.addAttribute("availableFields", availableFields);

        return "export/statistics/configure";
    }

    /**
     * Сохранение настроек статистики
     */
    @PostMapping("/template/{templateId}/configure")
    public String saveConfig(@PathVariable Long templateId,
                             @ModelAttribute ExportStatisticsConfigDto config,
                             RedirectAttributes redirectAttributes) {
        log.debug("Сохранение настроек статистики для шаблона ID: {}", templateId);

        try {
            config.setTemplateId(templateId);
            statisticsService.saveStatisticsConfig(config);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Настройки статистики сохранены");

            return "redirect:/export/templates/" + templateId;

        } catch (Exception e) {
            log.error("Ошибка сохранения настроек статистики", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка сохранения: " + e.getMessage());
            return "redirect:/export/statistics/template/" + templateId + "/configure";
        }
    }

    /**
     * Страница сравнения статистики
     */
    @GetMapping("/compare")
    public String showComparePage(@RequestParam(required = false) Long templateId,
                                  @RequestParam(required = false) String sessionIds,
                                  @RequestParam(defaultValue = "false") boolean applyFilter,
                                  Model model) {
        log.debug("Показ страницы сравнения статистики");

        if (templateId == null) {
            model.addAttribute("error", "Не указан шаблон");
            return "export/statistics/compare";
        }

        // Парсим ID сессий
        List<Long> sessionIdList = new ArrayList<>();
        if (sessionIds != null && !sessionIds.isEmpty()) {
            sessionIdList = Arrays.stream(sessionIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        }

        // Получаем все доступные сессии для выбора
        List<ExportSession> availableSessions = sessionRepository
                .findByTemplateIdOrderByStartedAtDesc(templateId);

        // Получаем настройки статистики
        ExportStatisticsConfigDto config = statisticsService.getStatisticsConfig(templateId)
                .orElse(null);

        if (config == null || !config.getIsEnabled()) {
            model.addAttribute("error", "Статистика не настроена для данного шаблона");
            model.addAttribute("templateId", templateId);
            return "export/statistics/compare";
        }

        // Получаем данные статистики
        ExportStatisticsDto statistics = statisticsService.compareExports(
                sessionIdList, templateId, applyFilter);

        model.addAttribute("templateId", templateId);
        model.addAttribute("statistics", statistics);
        model.addAttribute("config", config);
        model.addAttribute("availableSessions", availableSessions);
        model.addAttribute("selectedSessionIds", sessionIdList);
        model.addAttribute("filterApplied", applyFilter);

        return "export/statistics/compare";
    }

    /**
     * API для обновления статистики (AJAX)
     */
    @GetMapping("/api/compare")
    @ResponseBody
    public ExportStatisticsDto getComparisonData(
            @RequestParam Long templateId,
            @RequestParam(required = false) List<Long> sessionIds,
            @RequestParam(defaultValue = "false") boolean applyFilter) {

        log.debug("API запрос статистики для шаблона ID: {}", templateId);

        return statisticsService.compareExports(sessionIds, templateId, applyFilter);
    }

    /**
     * Пересчет статистики для сессии
     */
    @PostMapping("/session/{sessionId}/recalculate")
    public String recalculateStatistics(@PathVariable Long sessionId,
                                        RedirectAttributes redirectAttributes) {
        log.debug("Пересчет статистики для сессии ID: {}", sessionId);

        try {
            statisticsService.calculateStatistics(sessionId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Статистика пересчитана");
        } catch (Exception e) {
            log.error("Ошибка пересчета статистики", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка пересчета: " + e.getMessage());
        }

        return "redirect:/export/status/" + sessionId;
    }
}