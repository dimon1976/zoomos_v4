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
 * Контроллер для управления операциями клиента
 */
@Controller
@RequestMapping("/clients/{clientId}")
@RequiredArgsConstructor
@Slf4j
public class ClientOperationsController {

    private final ClientService clientService;

    /**
     * Отображение страницы операций клиента
     */
    @GetMapping("/operations")
    public String showOperationsPage(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to show operations page for client id: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/operations";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", clientId);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:" + UrlConstants.CLIENTS;
                });
    }

    /**
     * Отображение детальной информации об операции
     * Перенаправляется на ImportController для унифицированного отображения
     */
    @GetMapping("/operations/{operationId}")
    public String showOperationDetail(@PathVariable Long clientId, 
                                    @PathVariable Long operationId) {
        log.debug("GET request to show operation detail for client id: {} and operation id: {} - redirecting to unified controller", clientId, operationId);

        // Перенаправляем на унифицированный контроллер
        return "forward:" + UrlConstants.CLIENT_OPERATION_DETAIL
                .replace("{clientId}", clientId.toString())
                .replace("{operationId}", operationId.toString());
    }
}