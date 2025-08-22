package com.java.controller;

import com.java.constants.UrlConstants;
import com.java.dto.ClientDto;
import com.java.service.client.ClientService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контроллер для управления страницей шаблонов клиента.
 * Объединяет просмотр шаблонов импорта и экспорта.
 */
@Controller
@RequestMapping("/clients/{clientId}")
@RequiredArgsConstructor
@Slf4j
public class ClientTemplatesController {

    private final ClientService clientService;

    /**
     * Отображение страницы шаблонов клиента (объединенная страница)
     */
    @GetMapping("/templates")
    public String showTemplatesPage(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to show templates page for client id: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/templates";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", clientId);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }
}