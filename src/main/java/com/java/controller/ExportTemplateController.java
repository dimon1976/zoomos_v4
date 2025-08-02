package com.java.controller;

import com.java.dto.ExportTemplateDto;

import com.java.model.enums.EntityType;
import com.java.model.enums.ExportStrategy;
import com.java.service.exports.ExportTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Контроллер для управления шаблонами экспорта
 */
@Controller
@RequestMapping("/export/templates")
@RequiredArgsConstructor
@Slf4j
public class ExportTemplateController {

    private final ExportTemplateService templateService;

    /**
     * Список шаблонов клиента
     */
    @GetMapping("/client/{clientId}")
    public String listClientTemplates(@PathVariable Long clientId, Model model) {
        log.debug("GET запрос на получение шаблонов экспорта клиента ID: {}", clientId);

        List<ExportTemplateDto> templates = templateService.getClientTemplates(clientId);
        model.addAttribute("templates", templates);
        model.addAttribute("clientId", clientId);

        return "export/templates/list";
    }

    /**
     * Форма создания нового шаблона
     */
    @GetMapping("/client/{clientId}/create")
    public String showCreateForm(@PathVariable Long clientId, Model model) {
        log.debug("GET запрос на создание шаблона экспорта для клиента ID: {}", clientId);

        ExportTemplateDto template = new ExportTemplateDto();
        template.setClientId(clientId);

        // Добавляем пустые списки для полей и фильтров
        template.setFields(new ArrayList<>());
        template.setFilters(new ArrayList<>());

        model.addAttribute("template", template);
        model.addAttribute("clientId", clientId);
        model.addAttribute("entityTypes", EntityType.values());
        model.addAttribute("exportStrategies", ExportStrategy.values());
        model.addAttribute("filterTypes", FilterType.values());

        return "export/templates/form";
    }

    /**
     * Создание нового шаблона
     */
    @PostMapping("/create")
    public String createTemplate(@Valid @ModelAttribute ExportTemplateDto template,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на создание шаблона экспорта: {}", template.getName());

        if (bindingResult.hasErrors()) {
            return "export/templates/form";
        }

        try {
            ExportTemplateDto created = templateService.createTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон '" + created.getName() + "' успешно создан");

            return "redirect:/export/templates/" + created.getId();

        } catch (Exception e) {
            log.error("Ошибка создания шаблона экспорта", e);
            bindingResult.reject("global.error", e.getMessage());
            return "export/templates/form";
        }
    }

    /**
     * Просмотр деталей шаблона
     */
    @GetMapping("/{templateId}")
    public String viewTemplate(@PathVariable Long templateId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на просмотр шаблона экспорта ID: {}", templateId);

        return templateService.getTemplate(templateId)
                .map(template -> {
                    model.addAttribute("template", template);
                    return "export/templates/view";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Шаблон не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Форма редактирования шаблона
     */
    @GetMapping("/{templateId}/edit")
    public String showEditForm(@PathVariable Long templateId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на редактирование шаблона экспорта ID: {}", templateId);

        return templateService.getTemplate(templateId)
                .map(template -> {
                    model.addAttribute("template", template);
                    model.addAttribute("templateId", templateId);
                    model.addAttribute("entityTypes", EntityType.values());
                    model.addAttribute("exportStrategies", ExportStrategy.values());
                    model.addAttribute("filterTypes", FilterType.values());
                    return "export/templates/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Шаблон не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Обновление шаблона
     */
    @PostMapping("/{templateId}/edit")
    public String updateTemplate(@PathVariable Long templateId,
                                 @Valid @ModelAttribute ExportTemplateDto template,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на обновление шаблона экспорта ID: {}", templateId);

        if (bindingResult.hasErrors()) {
            return "export/templates/form";
        }

        try {
            ExportTemplateDto updated = templateService.updateTemplate(templateId, template);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон '" + updated.getName() + "' успешно обновлен");

            return "redirect:/export/templates/" + templateId;

        } catch (Exception e) {
            log.error("Ошибка обновления шаблона экспорта", e);
            bindingResult.reject("global.error", e.getMessage());
            return "export/templates/form";
        }
    }

    /**
     * Удаление шаблона
     */
    @PostMapping("/{templateId}/delete")
    public String deleteTemplate(@PathVariable Long templateId,
                                 @RequestParam Long clientId,
                                 RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на удаление шаблона экспорта ID: {}", templateId);

        try {
            templateService.deleteTemplate(templateId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон успешно удален");
        } catch (Exception e) {
            log.error("Ошибка удаления шаблона экспорта", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка удаления шаблона: " + e.getMessage());
        }

        return "redirect:/export/templates/client/" + clientId;
    }

    /**
     * Клонирование шаблона
     */
    @PostMapping("/{templateId}/clone")
    public String cloneTemplate(@PathVariable Long templateId,
                                @RequestParam String newName,
                                RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на клонирование шаблона экспорта ID: {} с именем: {}",
                templateId, newName);

        try {
            ExportTemplateDto cloned = templateService.cloneTemplate(templateId, newName);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон успешно клонирован");

            return "redirect:/export/templates/" + cloned.getId();

        } catch (Exception e) {
            log.error("Ошибка клонирования шаблона экспорта", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка клонирования шаблона: " + e.getMessage());
            return "redirect:/export/templates/" + templateId;
        }
    }

    /**
     * API для получения полей сущности
     */
    @GetMapping("/api/entity-fields/{entityType}")
    @ResponseBody
    public List<FieldInfo> getEntityFields(@PathVariable String entityType) {
        log.debug("Запрос полей для типа сущности: {}", entityType);

        List<FieldInfo> fields = new ArrayList<>();

        if ("AV_DATA".equals(entityType)) {
            fields.add(new FieldInfo("id", "ID", "Идентификатор записи"));
            fields.add(new FieldInfo("createdAt", "Дата создания", "Дата и время создания"));
            fields.add(new FieldInfo("dataSource", "Источник данных", "Тип источника данных"));
            fields.add(new FieldInfo("productId", "ID товара", "Идентификатор товара"));
            fields.add(new FieldInfo("productName", "Название товара", "Наименование товара"));
            fields.add(new FieldInfo("productBrand", "Бренд", "Производитель товара"));
            fields.add(new FieldInfo("productBar", "Штрихкод", "Штриховой код товара"));
            fields.add(new FieldInfo("productPrice", "Цена", "Цена товара"));
            fields.add(new FieldInfo("region", "Регион", "Регион"));
            fields.add(new FieldInfo("regionAddress", "Адрес", "Адрес в регионе"));
            fields.add(new FieldInfo("competitorName", "Конкурент", "Название конкурента"));
            fields.add(new FieldInfo("competitorPrice", "Цена конкурента", "Цена у конкурента"));
            fields.add(new FieldInfo("competitorDate", "Дата", "Дата сбора данных"));
            // Добавьте остальные поля по необходимости
        } else if ("AV_HANDBOOK".equals(entityType)) {
            fields.add(new FieldInfo("id", "ID", "Идентификатор записи"));
            fields.add(new FieldInfo("handbookRetailNetworkCode", "Код сети", "Код розничной сети"));
            fields.add(new FieldInfo("handbookRetailNetwork", "Розничная сеть", "Название сети"));
            fields.add(new FieldInfo("handbookPhysicalAddress", "Адрес", "Физический адрес"));
            fields.add(new FieldInfo("handbookWebSite", "Сайт", "Веб-сайт"));
            fields.add(new FieldInfo("handbookRegionCode", "Код региона", "Код региона"));
            fields.add(new FieldInfo("handbookRegionName", "Регион", "Название региона"));
        }

        return fields;
    }

    /**
     * Класс для информации о поле
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class FieldInfo {
        private String fieldName;
        private String displayName;
        private String description;
    }
}