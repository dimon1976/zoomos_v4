package com.java.controller;

import com.java.dto.ClientDto;
import com.java.model.entity.ZoomosCheckRun;
import com.java.model.entity.ZoomosShop;
import com.java.model.entity.ZoomosShopSchedule;
import com.java.repository.ZoomosCheckRunRepository;
import com.java.repository.ZoomosShopRepository;
import com.java.repository.ZoomosShopScheduleRepository;
import com.java.service.ZoomosParserService;
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

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private final ClientService clientService;
    private final ZoomosCheckRunRepository checkRunRepository;
    private final ZoomosShopScheduleRepository scheduleRepository;
    private final ZoomosShopRepository shopRepository;
    private final ZoomosParserService parserService;

    @Value("${spring.servlet.multipart.max-request-size}")
    private DataSize maxRequestSize;

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
            return "redirect:/clients";
        } catch (IllegalArgumentException e) {
            log.error("Error creating client: {}", e.getMessage());
            result.rejectValue("name", "error.client", e.getMessage());
            return "clients/form";
        }
    }

    /**
     * Отображение данных клиента
     */
    @GetMapping("/{id}")
    public String getClientDetails(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to get client details for id: {}", id);

        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    // Последний run с проблемами для кнопки Zoomos Check
                    if (client.getLinkedShopId() != null) {
                        checkRunRepository.findByShopIdOrderByStartedAtDesc(client.getLinkedShopId())
                                .stream()
                                .filter(r -> "COMPLETED".equals(r.getStatus())
                                        && ((r.getErrorCount() != null && r.getErrorCount() > 0)
                                        || (r.getWarningCount() != null && r.getWarningCount() > 0)))
                                .findFirst()
                                .ifPresent(run -> model.addAttribute("lastZoomosRunId", run.getId()));
                    }
                    return "clients/details";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", id);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Отображение формы редактирования клиента
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to show edit form for client id: {}", id);

        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/form";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", id);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Обработка обновления данных клиента
     */
    @PostMapping("/{id}/edit")
    public String updateClient(@PathVariable Long id,
                               @Valid @ModelAttribute("client") ClientDto clientDto,
                               BindingResult result,
                               RedirectAttributes redirectAttributes) {
        log.debug("POST request to update client id: {}", id);

        if (result.hasErrors()) {
            log.debug("Validation errors detected: {}", result.getAllErrors());
            return "clients/form";
        }

        try {
            ClientDto updatedClient = clientService.updateClient(id, clientDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Клиент '" + updatedClient.getName() + "' успешно обновлен");
            return "redirect:/clients/" + id;
        } catch (EntityNotFoundException e) {
            log.error("Client not found for update: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/clients";
        } catch (IllegalArgumentException e) {
            log.error("Error updating client: {}", e.getMessage());
            result.rejectValue("name", "error.client", e.getMessage());
            return "clients/form";
        }
    }

    /**
     * Удаление клиента
     */
    @PostMapping("/{id}/delete")
    public String deleteClient(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.debug("POST request to delete client id: {}", id);

        // Получаем имя клиента перед удалением для сообщения
        String clientName = clientService.getClientById(id)
                .map(ClientDto::getName)
                .orElse("неизвестный");

        if (clientService.deleteClient(id)) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Клиент '" + clientName + "' успешно удален");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Клиент с ID " + id + " не найден");
        }

        return "redirect:/clients";
    }

    /**
     * Отображение страницы импорта клиента
     */
    @GetMapping("/{id}/import")
    public String getClientImportPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to get client import page for id: {}", id);

        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("maxRequestSizeBytes", maxRequestSize.toBytes());
                    return "clients/import";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", id);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Отображение страницы экспорта клиента
     */
    @GetMapping("/{id}/export")
    public String getClientExportPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to get client export page for id: {}", id);

        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/export";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", id);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Отображение страницы управления шаблонами клиента
     */
    @GetMapping("/{id}/templates")
    public String getClientTemplatesPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to get client templates page for id: {}", id);

        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/templates";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", id);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Отображение страницы истории операций клиента
     */
    @GetMapping("/{id}/operations")
    public String getClientOperationsPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to get client operations page for id: {}", id);

        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/operations";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", id);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Отображение страницы статистики клиента
     */
    @GetMapping("/{id}/statistics")
    public String getClientStatisticsPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to get client statistics page for id: {}", id);

        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/statistics";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", id);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
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

    // =========================================================================
    // Zoomos Check интеграция
    // =========================================================================

    /**
     * Страница Zoomos Check для клиента
     */
    @GetMapping("/{id}/zoomos")
    public String clientZoomosPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    Optional<ZoomosShop> linkedShop = clientService.getLinkedShop(id);
                    model.addAttribute("linkedShop", linkedShop.orElse(null));
                    if (linkedShop.isPresent()) {
                        Long shopId = linkedShop.get().getId();
                        List<ZoomosCheckRun> runs = checkRunRepository
                                .findByShopIdOrderByStartedAtDesc(shopId)
                                .stream().limit(10).collect(Collectors.toList());
                        model.addAttribute("checkRuns", runs);
                        List<ZoomosShopSchedule> schedules = scheduleRepository.findAllByShopId(shopId);
                        model.addAttribute("schedules", schedules);
                    } else {
                        model.addAttribute("checkRuns", List.of());
                        model.addAttribute("schedules", List.of());
                        model.addAttribute("allShops", parserService.getAllShops());
                    }
                    return "clients/zoomos";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * API: данные виджета Zoomos Check для клиента (async)
     */
    @GetMapping("/{id}/zoomos/widget")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> zoomosWidget(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        Optional<ZoomosShop> shopOpt = clientService.getLinkedShop(id);
        if (shopOpt.isEmpty()) {
            result.put("shopId", null);
            return ResponseEntity.ok(result);
        }
        ZoomosShop shop = shopOpt.get();
        result.put("shopId", shop.getId());
        result.put("shopName", shop.getShopName());
        checkRunRepository.findFirstByShopIdOrderByStartedAtDesc(shop.getId()).ifPresent(run -> {
            result.put("lastRunId", run.getId());
            result.put("lastRunStatus", run.getStatus());
            result.put("lastRunAt", run.getStartedAt() != null
                    ? run.getStartedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) : null);
            result.put("okCount", run.getOkCount());
            result.put("warnCount", run.getWarningCount());
            result.put("errorCount", run.getErrorCount());
        });
        result.put("hasSchedule", scheduleRepository.findFirstByShopId(shop.getId())
                .map(ZoomosShopSchedule::isEnabled).orElse(false));
        return ResponseEntity.ok(result);
    }

    /**
     * Привязать магазин Zoomos Check к клиенту
     */
    @PostMapping("/{id}/zoomos/link")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> linkShop(@PathVariable Long id, @RequestParam Long shopId) {
        try {
            clientService.linkShopToClient(id, shopId);
            ZoomosShop shop = shopRepository.findById(shopId).orElseThrow();
            return ResponseEntity.ok(Map.of("success", true, "shopName", shop.getShopName()));
        } catch (Exception e) {
            log.error("Ошибка привязки магазина {} к клиенту {}: {}", shopId, id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Отвязать магазин Zoomos Check от клиента
     */
    @PostMapping("/{id}/zoomos/unlink")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unlinkShop(@PathVariable Long id) {
        try {
            clientService.getLinkedShop(id).ifPresent(shop -> clientService.unlinkShopFromClient(shop.getId()));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Ошибка отвязки магазина от клиента {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}