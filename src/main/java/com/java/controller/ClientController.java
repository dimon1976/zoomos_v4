package com.java.controller;

import com.java.dto.ClientDto;
import com.java.service.client.ClientService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private final ClientService clientService;

    @Value("${spring.servlet.multipart.max-request-size}")
    private DataSize maxRequestSize;

    @GetMapping
    public String getAllClients(Model model, HttpServletRequest request) {
        model.addAttribute("clients", clientService.getAllClients());
        model.addAttribute("currentUri", request.getRequestURI());
        return "clients/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("client", new ClientDto());
        return "clients/form";
    }

    @PostMapping("/create")
    public String createClient(@Valid @ModelAttribute("client") ClientDto clientDto,
                               BindingResult result,
                               RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "clients/form";
        }
        try {
            ClientDto createdClient = clientService.createClient(clientDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Клиент '" + createdClient.getName() + "' успешно создан");
            return "redirect:/clients";
        } catch (IllegalArgumentException e) {
            result.rejectValue("name", "error.client", e.getMessage());
            return "clients/form";
        }
    }

    @GetMapping("/{id}")
    public String getClientDetails(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/details";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    @PostMapping("/{id}/edit")
    public String updateClient(@PathVariable Long id,
                               @Valid @ModelAttribute("client") ClientDto clientDto,
                               BindingResult result,
                               RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "clients/form";
        }
        try {
            ClientDto updatedClient = clientService.updateClient(id, clientDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Клиент '" + updatedClient.getName() + "' успешно обновлен");
            return "redirect:/clients/" + id;
        } catch (EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/clients";
        } catch (IllegalArgumentException e) {
            result.rejectValue("name", "error.client", e.getMessage());
            return "clients/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteClient(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        String clientName = clientService.getClientById(id)
                .map(ClientDto::getName)
                .orElse("неизвестный");
        if (clientService.deleteClient(id)) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Клиент '" + clientName + "' успешно удален");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + id + " не найден");
        }
        return "redirect:/clients";
    }

    @GetMapping("/{id}/import")
    public String getClientImportPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("maxRequestSizeBytes", maxRequestSize.toBytes());
                    return "clients/import";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    @GetMapping("/{id}/export")
    public String getClientExportPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/export";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    @GetMapping("/{id}/templates")
    public String getClientTemplatesPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/templates";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    @GetMapping("/{id}/operations")
    public String getClientOperationsPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/operations";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    @GetMapping("/{id}/statistics")
    public String getClientStatisticsPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/statistics";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    @GetMapping("/search")
    public String searchClients(@RequestParam String query, Model model) {
        model.addAttribute("clients", clientService.searchClients(query));
        model.addAttribute("searchQuery", query);
        return "clients/list";
    }

    @PostMapping("/{id}/toggle-active")
    public String toggleActive(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            clientService.toggleActive(id);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/clients";
    }

    @PostMapping("/reorder")
    @ResponseBody
    public ResponseEntity<Void> reorder(@RequestBody List<Long> ids) {
        clientService.reorder(ids);
        return ResponseEntity.ok().build();
    }
}
