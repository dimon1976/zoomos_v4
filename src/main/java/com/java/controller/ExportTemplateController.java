package com.java.controller;

import com.java.dto.ExportTemplateDto;
import com.java.dto.ExportTemplateFieldDto;
import com.java.dto.ExportTemplateFilterDto;
import com.java.service.EntityFieldService;
import com.java.service.client.ClientService;
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
@RequestMapping("/clients/{clientId}/export/templates")
@RequiredArgsConstructor
@Slf4j
public class ExportTemplateController {

    private final ExportTemplateService templateService;
    private final EntityFieldService fieldService;
    private final ClientService clientService;

    /**
     * Форма создания шаблона
     */
    @GetMapping
    public String listTemplates(@PathVariable Long clientId, Model model) {
        model.addAttribute("templates", templateService.getClientTemplates(clientId));
        model.addAttribute("clientId", clientId);
        model.addAttribute("clients", clientService.getAllClients());
        return "export/templates/list";
    }

    @GetMapping("/create")
    public String showCreateForm(@PathVariable Long clientId, Model model) {
        ExportTemplateDto template = new ExportTemplateDto();
        template.setClientId(clientId);
        model.addAttribute("template", template);
        model.addAttribute("clientId", clientId);
        populateAvailableFields(model, template);
        return "export/templates/form";
    }

    /**
     * Создание шаблона
     */
    @PostMapping("/create")
    public String createTemplate(@PathVariable Long clientId,
                                 @Valid @ModelAttribute("template") ExportTemplateDto template,
                                 BindingResult bindingResult,
                                 @RequestParam(required = false) String addField,
                                 @RequestParam(required = false) Integer removeField,
                                 @RequestParam(required = false) String addFilter,
                                 @RequestParam(required = false) Integer removeFilter,
                                 @RequestParam(required = false) List<String> statisticsCountFields,
                                 @RequestParam(required = false) List<String> statisticsFilterFields,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {

        // Обрабатываем поля статистики
        if (Boolean.TRUE.equals(template.getEnableStatistics())) {
            template.setStatisticsCountFields(statisticsCountFields != null ? statisticsCountFields : new ArrayList<>());
            template.setStatisticsFilterFields(statisticsFilterFields != null ? statisticsFilterFields : new ArrayList<>());
        } else {
            // Очищаем настройки статистики если она отключена
            template.setEnableStatistics(false);
            template.setStatisticsCountFields(new ArrayList<>());
            template.setStatisticsGroupField(null);
            template.setStatisticsFilterFields(new ArrayList<>());
        }

        String dynamic = handleDynamicFields(template, addField, removeField, addFilter, removeFilter, model);
        if (dynamic != null) {
            model.addAttribute("clientId", template.getClientId());
            return dynamic;
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("clientId", template.getClientId());
            populateAvailableFields(model, template);
            return "export/templates/form";
        }

        try {
            ExportTemplateDto created = templateService.createTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон '" + created.getName() + "' успешно создан");
            return "redirect:/clients/" + clientId + "/export/templates/" + created.getId();
        } catch (Exception e) {
            log.error("Ошибка создания шаблона", e);
            bindingResult.reject("global.error", e.getMessage());
            model.addAttribute("clientId", template.getClientId());
            populateAvailableFields(model, template);
            return "export/templates/form";
        }
    }

    /**
     * Просмотр шаблона
     */
    @GetMapping("/{templateId}")
    public String viewTemplate(@PathVariable Long clientId,
                               @PathVariable Long templateId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        return templateService.getTemplate(templateId)
                .map(t -> {
                    model.addAttribute("template", t);
                    model.addAttribute("clients", clientService.getAllClients());
                    return "export/templates/view";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Шаблон не найден");
                    return "redirect:/clients/" + clientId + "/export/templates";
                });
    }

    /**
     * Форма редактирования
     */
    @GetMapping("/{templateId}/edit")
    public String showEditForm(@PathVariable Long clientId,
                               @PathVariable Long templateId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        return templateService.getTemplate(templateId)
                .map(t -> {
                    model.addAttribute("template", t);
                    model.addAttribute("templateId", templateId);
                    model.addAttribute("clientId", clientId);
                    populateAvailableFields(model, t);
                    return "export/templates/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Шаблон не найден");
                    return "redirect:/clients/" + clientId + "/export/templates";
                });
    }

    /**
     * Обновление шаблона
     */
    @PostMapping("/{templateId}/edit")
    public String updateTemplate(@PathVariable Long clientId,
                                 @PathVariable Long templateId,
                                 @Valid @ModelAttribute("template") ExportTemplateDto template,
                                 BindingResult bindingResult,
                                 @RequestParam(required = false) String addField,
                                 @RequestParam(required = false) Integer removeField,
                                 @RequestParam(required = false) String addFilter,
                                 @RequestParam(required = false) Integer removeFilter,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        String dynamic = handleDynamicFields(template, addField, removeField, addFilter, removeFilter, model);
        if (dynamic != null) {
            model.addAttribute("templateId", templateId);
            model.addAttribute("clientId", clientId);
            return dynamic;
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("templateId", templateId);
            model.addAttribute("clientId", clientId);
            populateAvailableFields(model, template);
            return "export/templates/form";
        }

        try {
            ExportTemplateDto updated = templateService.updateTemplate(templateId, template);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон '" + updated.getName() + "' успешно обновлен");
            return "redirect:/clients/" + clientId + "/export/templates/" + templateId;
        } catch (Exception e) {
            log.error("Ошибка обновления шаблона", e);
            bindingResult.reject("global.error", e.getMessage());
            model.addAttribute("templateId", templateId);
            model.addAttribute("clientId", clientId);
            populateAvailableFields(model, template);
            return "export/templates/form";
        }
    }

    /**
     * Удаление шаблона
     */
    @PostMapping("/{templateId}/delete")
    public String deleteTemplate(@PathVariable Long clientId,
                                 @PathVariable Long templateId,
                                 RedirectAttributes redirectAttributes) {
        try {
            templateService.deleteTemplate(templateId);
            redirectAttributes.addFlashAttribute("successMessage", "Шаблон успешно удален");
        } catch (Exception e) {
            log.error("Ошибка удаления шаблона", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка удаления шаблона: " + e.getMessage());
        }
        return "redirect:/clients/" + clientId + "/export/templates";
    }


    /**
     * Клонирование шаблона
     */
    @PostMapping("/{templateId}/clone")
    public String cloneTemplate(@PathVariable Long clientId,
                                @PathVariable Long templateId,
                                @RequestParam String newName,
                                RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на клонирование шаблона ID: {} с именем: {} для клиента {}",
                templateId, newName, clientId);

        try {
            ExportTemplateDto cloned = templateService.cloneTemplate(templateId, newName, clientId);
            redirectAttributes.addFlashAttribute("successMessage", "Шаблон успешно клонирован");
            return "redirect:/clients/" + clientId + "/export/templates/" + cloned.getId();
        } catch (Exception e) {
            log.error("Ошибка клонирования шаблона", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка клонирования шаблона: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/export/templates/" + templateId;
        }
    }

    private String handleDynamicFields(ExportTemplateDto template,
                                       String addField,
                                       Integer removeField,
                                       String addFilter,
                                       Integer removeFilter,
                                       Model model) {
        if (addField != null) {
            template.getFields().add(new ExportTemplateFieldDto());
            model.addAttribute("template", template);
            model.addAttribute("clientId", template.getClientId());
            populateAvailableFields(model, template);
            return "export/templates/form";
        }
        if (removeField != null && removeField >= 0 && removeField < template.getFields().size()) {
            template.getFields().remove((int) removeField);
            model.addAttribute("template", template);
            model.addAttribute("clientId", template.getClientId());
            populateAvailableFields(model, template);
            return "export/templates/form";
        }
        if (addFilter != null) {
            template.getFilters().add(new ExportTemplateFilterDto());
            model.addAttribute("template", template);
            model.addAttribute("clientId", template.getClientId());
            populateAvailableFields(model, template);
            return "export/templates/form";
        }
        if (removeFilter != null && removeFilter >= 0 && removeFilter < template.getFilters().size()) {
            template.getFilters().remove((int) removeFilter);
            model.addAttribute("template", template);
            model.addAttribute("clientId", template.getClientId());
            populateAvailableFields(model, template);
            return "export/templates/form";
        }
        return null;
    }

    private void populateAvailableFields(Model model, ExportTemplateDto template) {
        model.addAttribute("availableFields", fieldService.getFields(template.getEntityType()));
    }
}
