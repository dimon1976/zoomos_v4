// src/main/java/com/java/controller/ClientController.java
package com.java.controller;

import com.java.constants.UrlConstants;
import com.java.dto.ClientDto;
import com.java.service.client.ClientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(UrlConstants.CLIENTS)
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private final ClientService clientService;

    /**
     * Отображение списка всех клиентов
     */
    @GetMapping
    public String getAllClients(Model model, HttpServletRequest request) {
        log.debug("GET request to get all clients");
        model.addAttribute("clients", clientService.getAllClients());
        model.addAttribute("currentUri", request.getRequestURI());
        return "clients/list";
    }

    /**
     * Отображение формы создания нового клиента
     */
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        log.debug("GET request to show create client form");
        model.addAttribute("client", new ClientDto());
        return "clients/form";
    }

    /**
     * Обработка создания нового клиента
     */
    @PostMapping("/create")
    public String createClient(@Valid @ModelAttribute("client") ClientDto clientDto,
                               BindingResult result,
                               RedirectAttributes redirectAttributes) {
        log.debug("POST request to create a client: {}", clientDto);

        if (result.hasErrors()) {
            log.debug("Validation errors detected: {}", result.getAllErrors());
            return "clients/form";
        }

        try {
            ClientDto createdClient = clientService.createClient(clientDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Клиент '" + createdClient.getName() + "' успешно создан");
            return "redirect:" + UrlConstants.CLIENTS;
        } catch (IllegalArgumentException e) {
            log.error("Error creating client: {}", e.getMessage());
            result.rejectValue("name", "error.client", e.getMessage());
            return "clients/form";
        }
    }

    /**
     * Удаление клиента
     */
    @PostMapping("/{clientId}/delete")
    public String deleteClient(@PathVariable Long clientId, RedirectAttributes redirectAttributes) {
        log.debug("POST request to delete client id: {}", clientId);

        // Получаем имя клиента перед удалением для сообщения
        String clientName = clientService.getClientById(clientId)
                .map(ClientDto::getName)
                .orElse("неизвестный");

        if (clientService.deleteClient(clientId)) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Клиент '" + clientName + "' успешно удален");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Клиент с ID " + clientId + " не найден");
        }

        return "redirect:" + UrlConstants.CLIENTS;
    }

    /**
     * Поиск клиентов
     */
    @GetMapping("/search")
    public String searchClients(@RequestParam String query, Model model) {
        log.debug("GET request to search clients with query: {}", query);

        model.addAttribute("clients", clientService.searchClients(query));
        model.addAttribute("searchQuery", query);
        return "clients/list";
    }
}