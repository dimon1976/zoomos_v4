package com.java.controller;

import com.java.dto.ClientDto;
import com.java.dto.DashboardStatsDto;
import com.java.model.FileOperation;
import com.java.service.client.ClientService;
import com.java.service.dashboard.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Контроллер для главной страницы приложения
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class MainController {

    private final DashboardService dashboardService;
    private final ClientService clientService;

    /**
     * Отображение главной страницы приложения
     */
    @GetMapping("/")
    public String displayHomePage(Model model, HttpServletRequest request) {
        log.debug("Запрос на отображение главной страницы");

        try {
            // Получаем начальную статистику для дашборда
            DashboardStatsDto stats = dashboardService.getDashboardStats();

            // Получаем список клиентов для фильтра
            List<ClientDto> clients = clientService.getAllClients();

            // Получаем доступные типы файлов
            List<String> fileTypes = dashboardService.getAvailableFileTypes();

            // Получаем доступные типы операций и статусы
            FileOperation.OperationType[] operationTypes = FileOperation.OperationType.values();
            FileOperation.OperationStatus[] operationStatuses = FileOperation.OperationStatus.values();

            model.addAttribute("dashboardStats", stats);
            model.addAttribute("clients", clients);
            model.addAttribute("fileTypes", fileTypes);
            model.addAttribute("operationTypes", operationTypes);
            model.addAttribute("operationStatuses", operationStatuses);
            model.addAttribute("currentUri", request.getRequestURI());
        } catch (Exception e) {
            log.error("Ошибка при подготовке данных для главной страницы: ", e);
            // Возвращаем простую страницу ошибки
            model.addAttribute("pageTitle", "Ошибка");
            return "error/simple";
        }

        return "index";
    }
}