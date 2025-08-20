package com.java.controller;

import com.java.constants.UrlConstants;
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
@RequiredArgsConstructor
@Slf4j
public class ExportTemplateController {

    private final ExportTemplateService templateService;
    private final EntityFieldService fieldService;
    private final ClientService clientService;

    /**
     * Список шаблонов экспорта клиента
     */
    @GetMapping(UrlConstants.CLIENT_EXPORT_TEMPLATES)
    public String listTemplates(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на получение шаблонов экспорта для клиента ID: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    List<ExportTemplateDto> templates = templateService.getClientTemplates(clientId);
                    model.addAttribute("templates", templates);
                    model.addAttribute("client", client);
                    model.addAttribute("clientId", clientId);
                    return "clients/export/templates/list";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    /**
     * Форма создания шаблона
     */
    @GetMapping(UrlConstants.CLIENT_EXPORT_TEMPLATES_CREATE)
    public String showCreateForm(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на отображение формы создания шаблона экспорта для клиента ID: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    ExportTemplateDto template = new ExportTemplateDto();
                    template.setClientId(clientId);
                    model.addAttribute("template", template);
                    model.addAttribute("client", client);
                    model.addAttribute("clientId", clientId);
                    populateAvailableFields(model, template);
                    return "clients/export/templates/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    /**
     * Создание шаблона
     */
    @PostMapping(UrlConstants.CLIENT_EXPORT_TEMPLATES_CREATE)
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

        template.setClientId(clientId);

        // Обрабатываем поля статистики
        if (Boolean.TRUE.equals(template.getEnableStatistics())) {
            template.setStatisticsCountFields(statisticsCountFields != null ? statisticsCountFields : new ArrayList<>());
            template.setStatisticsFilterFields(statisticsFilterFields != null ? statisticsFilterFields : new ArrayList<>());
        } else {
            template.setEnableStatistics(false);
            template.setStatisticsCountFields(new ArrayList<>());
            template.setStatisticsGroupField(null);
            template.setStatisticsFilterFields(new ArrayList<>());
        }

        String dynamic = handleDynamicFields(template, addField, removeField, addFilter, removeFilter, model);
        if (dynamic != null) {
            model.addAttribute("clientId", clientId);
            return dynamic;
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("clientId", clientId);
            populateAvailableFields(model, template);
            return "clients/export/templates/form";
        }

        try {
            ExportTemplateDto created = templateService.createTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон '" + created.getName() + "' успешно создан");
            return "redirect:" + UrlConstants.CLIENT_EXPORT_TEMPLATE_DETAIL
                    .replace("{clientId}", clientId.toString())
                    .replace("{templateId}", created.getId().toString());
        } catch (Exception e) {
            log.error("Ошибка создания шаблона", e);
            bindingResult.reject("global.error", e.getMessage());
            model.addAttribute("clientId", clientId);
            populateAvailableFields(model, template);
            return "clients/export/templates/form";
        }
    }

    /**
     * Просмотр шаблона
     */
    @GetMapping(UrlConstants.CLIENT_EXPORT_TEMPLATE_DETAIL)
    public String viewTemplate(@PathVariable Long clientId,
                               @PathVariable Long templateId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на просмотр шаблона экспорта ID: {} для клиента ID: {}", templateId, clientId);

        return templateService.getTemplate(templateId)
                .filter(template -> template.getClientId().equals(clientId))
                .map(template -> {
                    model.addAttribute("template", template);
                    model.addAttribute("clientId", clientId);
                    return "clients/export/templates/view";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Шаблон не найден");
                    return "redirect:" + UrlConstants.CLIENT_EXPORT_TEMPLATES
                            .replace("{clientId}", clientId.toString());
                });
    }

    /**
     * Форма редактирования
     */
    @GetMapping(UrlConstants.CLIENT_EXPORT_TEMPLATE_EDIT)
    public String showEditForm(@PathVariable Long clientId,
                               @PathVariable Long templateId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на редактирование шаблона экспорта ID: {} для клиента ID: {}", templateId, clientId);

        return templateService.getTemplate(templateId)
                .filter(template -> template.getClientId().equals(clientId))
                .map(template -> {
                    model.addAttribute("template", template);
                    model.addAttribute("templateId", templateId);
                    model.addAttribute("clientId", clientId);
                    populateAvailableFields(model, template);
                    return "clients/export/templates/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Шаблон не найден");
                    return "redirect:" + UrlConstants.CLIENT_EXPORT_TEMPLATES
                            .replace("{clientId}", clientId.toString());
                });
    }

    /**
     * Обновление шаблона
     */
    @PostMapping(UrlConstants.CLIENT_EXPORT_TEMPLATE_EDIT)
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
            return "clients/export/templates/form";
        }

        try {
            ExportTemplateDto updated = templateService.updateTemplate(templateId, template);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон '" + updated.getName() + "' успешно обновлен");
            return "redirect:" + UrlConstants.CLIENT_EXPORT_TEMPLATE_DETAIL
                    .replace("{clientId}", clientId.toString())
                    .replace("{templateId}", templateId.toString());
        } catch (Exception e) {
            log.error("Ошибка обновления шаблона", e);
            bindingResult.reject("global.error", e.getMessage());
            model.addAttribute("templateId", templateId);
            model.addAttribute("clientId", clientId);
            populateAvailableFields(model, template);
            return "clients/export/templates/form";
        }
    }

    /**
     * Удаление шаблона
     */
    @PostMapping(UrlConstants.CLIENT_EXPORT_TEMPLATE_DELETE)
    public String deleteTemplate(@PathVariable Long clientId,
                                 @PathVariable Long templateId,
                                 RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на удаление шаблона экспорта ID: {} для клиента ID: {}", templateId, clientId);

        try {
            templateService.deleteTemplate(templateId);
            redirectAttributes.addFlashAttribute("successMessage", "Шаблон успешно удален");
        } catch (Exception e) {
            log.error("Ошибка удаления шаблона", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка удаления шаблона: " + e.getMessage());
        }

        return "redirect:" + UrlConstants.CLIENT_EXPORT_TEMPLATES
                .replace("{clientId}", clientId.toString());
    }

    /**
     * Клонирование шаблона
     */
    @PostMapping(UrlConstants.CLIENT_EXPORT_TEMPLATE_CLONE)
    public String cloneTemplate(@PathVariable Long clientId,
                                @PathVariable Long templateId,
                                @RequestParam String newName,
                                @RequestParam Long targetClientId,
                                RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на клонирование шаблона экспорта ID: {} с именем: {} для клиента {}",
                templateId, newName, targetClientId);

        try {
            ExportTemplateDto cloned = templateService.cloneTemplate(templateId, newName, targetClientId);
            redirectAttributes.addFlashAttribute("successMessage", "Шаблон успешно клонирован");
            return "redirect:" + UrlConstants.CLIENT_EXPORT_TEMPLATE_DETAIL
                    .replace("{clientId}", targetClientId.toString())
                    .replace("{templateId}", cloned.getId().toString());
        } catch (Exception e) {
            log.error("Ошибка клонирования шаблона", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка клонирования шаблона: " + e.getMessage());
            return "redirect:" + UrlConstants.CLIENT_EXPORT_TEMPLATE_DETAIL
                    .replace("{clientId}", clientId.toString())
                    .replace("{templateId}", templateId.toString());
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
            populateAvailableFields(model, template);
            return "clients/export/templates/form";
        }
        if (removeField != null && removeField >= 0 && removeField < template.getFields().size()) {
            template.getFields().remove((int) removeField);
            model.addAttribute("template", template);
            populateAvailableFields(model, template);
            return "clients/export/templates/form";
        }
        if (addFilter != null) {
            template.getFilters().add(new ExportTemplateFilterDto());
            model.addAttribute("template", template);
            populateAvailableFields(model, template);
            return "clients/export/templates/form";
        }
        if (removeFilter != null && removeFilter >= 0 && removeFilter < template.getFilters().size()) {
            template.getFilters().remove((int) removeFilter);
            model.addAttribute("template", template);
            populateAvailableFields(model, template);
            return "clients/export/templates/form";
        }
        return null;
    }

    private void populateAvailableFields(Model model, ExportTemplateDto template) {
        if (template.getEntityType() != null) {
            model.addAttribute("availableFields", fieldService.getFields(template.getEntityType()));
        }
    }
}