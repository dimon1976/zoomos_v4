package com.java.controller;

import com.java.dto.ClientDto;
import com.java.dto.DashboardFilterDto;
import com.java.dto.DashboardOperationDto;
import com.java.dto.DashboardStatsDto;
import com.java.model.FileOperation;
import com.java.service.client.ClientService;
import com.java.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;
    private final ClientService clientService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Получение общей статистики для дашборда
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDto> getDashboardStats() {
        log.debug("Запрос статистики дашборда");
        DashboardStatsDto stats = dashboardService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Получение операций с фильтрацией
     */
    @GetMapping("/operations")
    public ResponseEntity<Page<DashboardOperationDto>> getOperations(DashboardFilterDto filter) {
        log.debug("Запрос операций с фильтрами: {}", filter);
        Page<DashboardOperationDto> operations = dashboardService.getFilteredOperations(filter);
        return ResponseEntity.ok(operations);
    }

    /**
     * Получение списка клиентов для фильтра
     */
    @GetMapping("/clients")
    public ResponseEntity<List<ClientDto>> getClientsForFilter() {
        List<ClientDto> clients = clientService.getAllClients();
        return ResponseEntity.ok(clients);
    }

    /**
     * Получение доступных типов файлов
     */
    @GetMapping("/file-types")
    public ResponseEntity<List<String>> getFileTypes() {
        List<String> fileTypes = dashboardService.getAvailableFileTypes();
        return ResponseEntity.ok(fileTypes);
    }

    /**
     * Автоматическое обновление статистики через WebSocket.
     * Выполняется каждые 30 секунд
     */
    @Scheduled(fixedRate = 30000)
    public void broadcastStatsUpdate() {
        try {
            DashboardStatsDto stats = dashboardService.getDashboardStats();
            messagingTemplate.convertAndSend("/topic/dashboard-stats", stats);
            log.trace("Отправлена обновленная статистика дашборда");
        } catch (Exception e) {
            log.error("Ошибка при отправке обновления статистики дашборда", e);
        }
    }

    /**
     * Принудительное обновление статистики
     */
    @GetMapping("/refresh")
    public ResponseEntity<DashboardStatsDto> refreshStats() {
        log.debug("Принудительное обновление статистики дашборда");
        DashboardStatsDto stats = dashboardService.getDashboardStats();

        // Отправляем обновление через WebSocket
        messagingTemplate.convertAndSend("/topic/dashboard-stats", stats);

        return ResponseEntity.ok(stats);
    }
}