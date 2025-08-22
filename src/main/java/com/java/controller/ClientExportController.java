package com.java.controller;

import com.java.constants.UrlConstants;
import com.java.dto.ClientDto;
import com.java.service.client.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clients/{clientId}")
@RequiredArgsConstructor
@Slf4j
public class ClientExportController {

    private final ClientService clientService;

    /**
     * Отображение страницы экспорта клиента
     */
    @GetMapping("/export")
    public String showExportPage(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to show export page for client id: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/export";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", clientId);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    /**
     * Страница запуска экспорта для клиента (совместимость со старыми ссылками)
     * Перенаправляет на ExportController для отображения формы экспорта
     */
    @GetMapping("/export/start")
    public String showExportStartPage(@PathVariable Long clientId, RedirectAttributes redirectAttributes) {
        log.debug("GET request to show export start page for client id: {}", clientId);
        
        // Перенаправляем на ExportController, который имеет полную форму экспорта
        return "redirect:/export/client/" + clientId;
    }
}