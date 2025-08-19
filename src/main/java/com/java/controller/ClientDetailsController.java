package com.java.controller;

import com.java.constants.UrlConstants;
import com.java.dto.ClientDto;
import com.java.service.client.ClientService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контроллер для страниц конкретного клиента
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ClientDetailsController {

    private final ClientService clientService;

    /**
     * Главная страница клиента (обзор)
     */
    @GetMapping(UrlConstants.CLIENT_DETAIL)
    public String clientOverview(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request for client overview, clientId: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("clientId", clientId);
                    return "clients/overview";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", clientId);
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + clientId + " не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    /**
     * Страница редактирования клиента
     */
    @GetMapping(UrlConstants.CLIENT_EDIT)
    public String editClient(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request for client edit form, clientId: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("clientId", clientId);
                    return "clients/edit";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", clientId);
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + clientId + " не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    /**
     * Обработка обновления клиента
     */
    @PostMapping(UrlConstants.CLIENT_EDIT)
    public String updateClient(@PathVariable Long clientId,
                               @Valid @ModelAttribute("client") ClientDto clientDto,
                               BindingResult result,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        log.debug("POST request to update client id: {}", clientId);

        if (result.hasErrors()) {
            log.debug("Validation errors detected: {}", result.getAllErrors());
            model.addAttribute("clientId", clientId);
            return "clients/edit";
        }

        try {
            ClientDto updatedClient = clientService.updateClient(clientId, clientDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Клиент '" + updatedClient.getName() + "' успешно обновлен");
            return "redirect:" + UrlConstants.CLIENT_DETAIL.replace("{clientId}", clientId.toString());
        } catch (EntityNotFoundException e) {
            log.error("Client not found for update: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:" + UrlConstants.CLIENTS;
        } catch (IllegalArgumentException e) {
            log.error("Error updating client: {}", e.getMessage());
            result.rejectValue("name", "error.client", e.getMessage());
            model.addAttribute("clientId", clientId);
            return "clients/edit";
        }
    }

    /**
     * Страница импорта клиента
     */
    @GetMapping(UrlConstants.CLIENT_IMPORT)
    public String importPage(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request for client import page, clientId: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("clientId", clientId);
                    return "clients/import";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + clientId + " не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    /**
     * Страница экспорта клиента
     */
    @GetMapping(UrlConstants.CLIENT_EXPORT)
    public String exportPage(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request for client export page, clientId: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("clientId", clientId);
                    return "clients/export";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + clientId + " не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    /**
     * Страница управления шаблонами
     */
    @GetMapping(UrlConstants.CLIENT_TEMPLATES)
    public String templatesPage(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request for client templates page, clientId: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("clientId", clientId);
                    return "clients/templates";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + clientId + " не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    /**
     * Страница статистики клиента
     */
    @GetMapping(UrlConstants.CLIENT_STATISTICS)
    public String statisticsPage(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request for client statistics page, clientId: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("clientId", clientId);
                    return "clients/statistics";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + clientId + " не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    /**
     * Страница операций клиента
     */
    @GetMapping(UrlConstants.CLIENT_OPERATIONS)
    public String operationsPage(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request for client operations page, clientId: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("clientId", clientId);
                    return "clients/operations";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + clientId + " не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }
}