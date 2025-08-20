package com.java.controller;

import com.java.constants.UrlConstants;
import com.java.dto.ImportTemplateDto;
import com.java.model.enums.EntityType;
import com.java.service.EntityFieldService;
import com.java.service.client.ClientService;
import com.java.service.imports.ImportTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ImportTemplateController {

    private final ImportTemplateService templateService;
    private final EntityFieldService fieldService;
    private final ClientService clientService;

    @GetMapping(UrlConstants.CLIENT_IMPORT_TEMPLATES)
    public String listClientTemplates(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на получение шаблонов клиента ID: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    List<ImportTemplateDto> templates = templateService.getClientTemplates(clientId);
                    model.addAttribute("templates", templates);
                    model.addAttribute("client", client);
                    model.addAttribute("clientId", clientId);
                    return "clients/import/templates/list";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    @GetMapping(UrlConstants.CLIENT_IMPORT_TEMPLATES_CREATE)
    public String showCreateForm(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на отображение формы создания шаблона для клиента ID: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    ImportTemplateDto template = new ImportTemplateDto();
                    template.setClientId(clientId);
                    model.addAttribute("template", template);
                    model.addAttribute("client", client);
                    model.addAttribute("clientId", clientId);
                    populateAvailableFields(model, template);
                    return "clients/import/templates/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    @PostMapping(UrlConstants.CLIENT_IMPORT_TEMPLATES_CREATE)
    public String createTemplate(@PathVariable Long clientId,
                                 @Valid @ModelAttribute ImportTemplateDto template,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на создание шаблона: {}", template.getName());

        template.setClientId(clientId);

        if (bindingResult.hasErrors()) {
            populateAvailableFields(model, template);
            model.addAttribute("clientId", clientId);
            return "clients/import/templates/form";
        }

        try {
            ImportTemplateDto created = templateService.createTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон '" + created.getName() + "' успешно создан");
            return "redirect:" + UrlConstants.CLIENT_IMPORT_TEMPLATE_DETAIL
                    .replace("{clientId}", clientId.toString())
                    .replace("{templateId}", created.getId().toString());
        } catch (Exception e) {
            log.error("Ошибка создания шаблона", e);
            bindingResult.reject("global.error", e.getMessage());
            populateAvailableFields(model, template);
            model.addAttribute("clientId", clientId);
            return "clients/import/templates/form";
        }
    }

    @GetMapping(UrlConstants.CLIENT_IMPORT_TEMPLATE_DETAIL)
    public String viewTemplate(@PathVariable Long clientId,
                               @PathVariable Long templateId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на просмотр шаблона ID: {} для клиента ID: {}", templateId, clientId);

        return templateService.getTemplate(templateId)
                .filter(template -> template.getClientId().equals(clientId))
                .map(template -> {
                    model.addAttribute("template", template);
                    model.addAttribute("clientId", clientId);
                    return "clients/import/templates/view";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Шаблон не найден");
                    return "redirect:" + UrlConstants.CLIENT_IMPORT_TEMPLATES
                            .replace("{clientId}", clientId.toString());
                });
    }

    @GetMapping(UrlConstants.CLIENT_IMPORT_TEMPLATE_EDIT)
    public String showEditForm(@PathVariable Long clientId,
                               @PathVariable Long templateId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на редактирование шаблона ID: {} для клиента ID: {}", templateId, clientId);

        return templateService.getTemplate(templateId)
                .filter(template -> template.getClientId().equals(clientId))
                .map(template -> {
                    model.addAttribute("template", template);
                    model.addAttribute("templateId", templateId);
                    model.addAttribute("clientId", clientId);
                    populateAvailableFields(model, template);
                    return "clients/import/templates/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Шаблон не найден");
                    return "redirect:" + UrlConstants.CLIENT_IMPORT_TEMPLATES
                            .replace("{clientId}", clientId.toString());
                });
    }

    @PostMapping(UrlConstants.CLIENT_IMPORT_TEMPLATE_EDIT)
    public String updateTemplate(@PathVariable Long clientId,
                                 @PathVariable Long templateId,
                                 @Valid @ModelAttribute ImportTemplateDto template,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на обновление шаблона ID: {} для клиента ID: {}", templateId, clientId);

        if (bindingResult.hasErrors()) {
            populateAvailableFields(model, template);
            model.addAttribute("templateId", templateId);
            model.addAttribute("clientId", clientId);
            return "clients/import/templates/form";
        }

        try {
            ImportTemplateDto updated = templateService.updateTemplate(templateId, template);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон '" + updated.getName() + "' успешно обновлен");
            return "redirect:" + UrlConstants.CLIENT_IMPORT_TEMPLATE_DETAIL
                    .replace("{clientId}", clientId.toString())
                    .replace("{templateId}", templateId.toString());
        } catch (Exception e) {
            log.error("Ошибка обновления шаблона", e);
            bindingResult.reject("global.error", e.getMessage());
            populateAvailableFields(model, template);
            model.addAttribute("templateId", templateId);
            model.addAttribute("clientId", clientId);
            return "clients/import/templates/form";
        }
    }

    @PostMapping(UrlConstants.CLIENT_IMPORT_TEMPLATE_DELETE)
    public String deleteTemplate(@PathVariable Long clientId,
                                 @PathVariable Long templateId,
                                 RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на удаление шаблона ID: {} для клиента ID: {}", templateId, clientId);

        try {
            templateService.deleteTemplate(templateId);
            redirectAttributes.addFlashAttribute("successMessage", "Шаблон успешно удален");
        } catch (Exception e) {
            log.error("Ошибка удаления шаблона", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка удаления шаблона: " + e.getMessage());
        }

        return "redirect:" + UrlConstants.CLIENT_IMPORT_TEMPLATES
                .replace("{clientId}", clientId.toString());
    }

    @PostMapping(UrlConstants.CLIENT_IMPORT_TEMPLATE_CLONE)
    public String cloneTemplate(@PathVariable Long clientId,
                                @PathVariable Long templateId,
                                @RequestParam String newName,
                                @RequestParam Long targetClientId,
                                RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на клонирование шаблона ID: {} с именем: {} для клиента {}",
                templateId, newName, targetClientId);

        try {
            ImportTemplateDto cloned = templateService.cloneTemplate(templateId, newName, targetClientId);
            redirectAttributes.addFlashAttribute("successMessage", "Шаблон успешно клонирован");
            return "redirect:" + UrlConstants.CLIENT_IMPORT_TEMPLATE_DETAIL
                    .replace("{clientId}", targetClientId.toString())
                    .replace("{templateId}", cloned.getId().toString());
        } catch (Exception e) {
            log.error("Ошибка клонирования шаблона", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка клонирования шаблона: " + e.getMessage());
            return "redirect:" + UrlConstants.CLIENT_IMPORT_TEMPLATE_DETAIL
                    .replace("{clientId}", clientId.toString())
                    .replace("{templateId}", templateId.toString());
        }
    }

    private void populateAvailableFields(Model model, ImportTemplateDto template) {
        if (template.getEntityType() != null) {
            model.addAttribute("availableFields", fieldService.getFields(template.getEntityType()));
        }
    }

    @GetMapping("/api/import/templates/available-fields")
    @ResponseBody
    public List<String> getAvailableFields(@RequestParam EntityType entityType) {
        return fieldService.getFields(entityType);
    }
}