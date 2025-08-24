package com.java.controller;

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

/**
 * Контроллер для управления шаблонами импорта
 */
@Controller
@RequestMapping("/clients/{clientId}/import/templates")
@RequiredArgsConstructor
@Slf4j
public class ImportTemplateController {

    private final ImportTemplateService templateService;
    private final EntityFieldService fieldService;
    private final ClientService clientService;

    /**
     * Отображение списка шаблонов клиента
     */
    @GetMapping
    public String listClientTemplates(@PathVariable Long clientId, Model model) {
        log.debug("GET запрос на получение шаблонов клиента ID: {}", clientId);

        List<ImportTemplateDto> templates = templateService.getClientTemplates(clientId);
        model.addAttribute("templates", templates);
        model.addAttribute("clientId", clientId);
        model.addAttribute("clients", clientService.getAllClients());

        return "import/templates/list";
    }

    /**
     * Форма создания нового шаблона
     */
    @GetMapping("/create")
    public String showCreateForm(@PathVariable Long clientId, Model model) {
        log.debug("GET запрос на отображение формы создания шаблона для клиента ID: {}", clientId);

        ImportTemplateDto template = new ImportTemplateDto();
        template.setClientId(clientId);

        model.addAttribute("template", template);
        model.addAttribute("clientId", clientId);
        populateAvailableFields(model, template);

        return "import/templates/form";
    }

    /**
     * Создание нового шаблона
     */
    @PostMapping("/create")
    public String createTemplate(@PathVariable Long clientId,
                                 @Valid @ModelAttribute ImportTemplateDto template,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на создание шаблона: {}", template.getName());

        if (bindingResult.hasErrors()) {
            populateAvailableFields(model, template);
            return "import/templates/form";
        }

        try {
            ImportTemplateDto created = templateService.createTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон '" + created.getName() + "' успешно создан");

            return "redirect:/clients/" + clientId + "/import/templates/" + created.getId();

        } catch (Exception e) {
            log.error("Ошибка создания шаблона", e);
            bindingResult.reject("global.error", e.getMessage());
            populateAvailableFields(model, template);
            return "import/templates/form";
        }
    }

    /**
     * Просмотр деталей шаблона
     */
    @GetMapping("/{templateId}")
    public String viewTemplate(@PathVariable Long clientId,
                               @PathVariable Long templateId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на просмотр шаблона ID: {}", templateId);

        return templateService.getTemplate(templateId)
                .map(template -> {
                    model.addAttribute("template", template);
                    model.addAttribute("clientId", clientId);
                    model.addAttribute("clients", clientService.getAllClients());
                    return "import/templates/view";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Шаблон не найден");
                    return "redirect:/clients/" + clientId + "/import/templates";
                });
    }

    /**
     * Форма редактирования шаблона
     */
    @GetMapping("/{templateId}/edit")
    public String showEditForm(@PathVariable Long clientId,
                               @PathVariable Long templateId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        log.debug("GET запрос на редактирование шаблона ID: {}", templateId);

        return templateService.getTemplate(templateId)
                .map(template -> {
                    model.addAttribute("template", template);
                    model.addAttribute("clientId", clientId);
                    model.addAttribute("templateId", templateId);
                    populateAvailableFields(model, template);
                    return "import/templates/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Шаблон не найден");
                    return "redirect:/clients/" + clientId + "/import/templates";
                });
    }

    /**
     * Обновление шаблона
     */
    @PostMapping("/{templateId}/edit")
    public String updateTemplate(@PathVariable Long clientId,
                                 @PathVariable Long templateId,
                                 @Valid @ModelAttribute ImportTemplateDto template,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на обновление шаблона ID: {}", templateId);

        if (bindingResult.hasErrors()) {
            populateAvailableFields(model, template);
            return "import/templates/form";
        }

        try {
            ImportTemplateDto updated = templateService.updateTemplate(templateId, template);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон '" + updated.getName() + "' успешно обновлен");

            return "redirect:/clients/" + clientId + "/import/templates/" + templateId;

        } catch (Exception e) {
            log.error("Ошибка обновления шаблона", e);
            bindingResult.reject("global.error", e.getMessage());
            populateAvailableFields(model, template);
            return "import/templates/form";
        }
    }

    private void populateAvailableFields(Model model, ImportTemplateDto template) {
        model.addAttribute("availableFields", fieldService.getFields(template.getEntityType()));
    }


    /**
     * Возвращает список доступных полей для заданного типа сущности
     */
    @GetMapping("/available-fields")
    @ResponseBody
    public List<String> getAvailableFields(@RequestParam EntityType entityType) {
        return fieldService.getFields(entityType);
    }

    /**
     * Удаление шаблона
     */
    @PostMapping("/{templateId}/delete")
    public String deleteTemplate(@PathVariable Long clientId,
                                 @PathVariable Long templateId,
                                 RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на удаление шаблона ID: {}", templateId);

        try {
            templateService.deleteTemplate(templateId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон успешно удален");
        } catch (Exception e) {
            log.error("Ошибка удаления шаблона", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка удаления шаблона: " + e.getMessage());
        }

        return "redirect:/clients/" + clientId + "/import/templates";
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
            ImportTemplateDto cloned = templateService.cloneTemplate(templateId, newName, clientId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон успешно клонирован");

            return "redirect:/clients/" + clientId + "/import/templates/" + cloned.getId();

        } catch (Exception e) {
            log.error("Ошибка клонирования шаблона", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка клонирования шаблона: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/import/templates/" + templateId;
        }
    }
}