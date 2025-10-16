package com.java.controller;

import com.java.dto.*;
import com.java.model.entity.DataCleanupHistory;
import com.java.service.maintenance.DataCleanupService;
import com.java.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Контроллер для управления очисткой устаревших данных
 */
@Controller
@RequestMapping("/maintenance/data-cleanup")
@Slf4j
@RequiredArgsConstructor
public class DataCleanupController {

    private final DataCleanupService cleanupService;
    private final ClientRepository clientRepository;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    /**
     * Страница управления очисткой данных
     */
    @GetMapping
    public String showCleanupPage(Model model) {
        log.info("Отображение страницы очистки данных");

        // Загружаем настройки
        List<DataCleanupSettingsDto> settings = cleanupService.getCleanupSettings();
        model.addAttribute("settings", settings);

        // Загружаем историю последних очисток
        List<DataCleanupHistory> history = cleanupService.getCleanupHistory(20);
        model.addAttribute("history", history);

        // Загружаем список клиентов для исключений
        model.addAttribute("clients", clientRepository.findAll());

        // Дата по умолчанию - 30 дней назад
        LocalDateTime defaultDate = LocalDateTime.now().minusDays(30);
        model.addAttribute("defaultDate", defaultDate.format(DATE_TIME_FORMATTER));

        // Минимально допустимая дата - 7 дней назад
        LocalDateTime minDate = LocalDateTime.now().minusDays(7);
        model.addAttribute("minDate", minDate.format(DATE_TIME_FORMATTER));

        return "maintenance/data-cleanup";
    }

    /**
     * Предпросмотр очистки - показывает что будет удалено без фактического удаления
     */
    @PostMapping("/preview")
    @ResponseBody
    public ResponseEntity<DataCleanupPreviewDto> previewCleanup(
            @RequestParam("cutoffDate") String cutoffDateStr,
            @RequestParam(value = "entityTypes", required = false) Set<String> entityTypes,
            @RequestParam(value = "excludedClientIds", required = false) Set<Long> excludedClientIds,
            @RequestParam(value = "batchSize", defaultValue = "10000") int batchSize) {

        log.info("Запрос на предпросмотр очистки: дата={}, типы={}, исключения={}",
                cutoffDateStr, entityTypes, excludedClientIds);

        try {
            LocalDateTime cutoffDate = LocalDateTime.parse(cutoffDateStr, DATE_TIME_FORMATTER);

            DataCleanupRequestDto request = DataCleanupRequestDto.builder()
                    .cutoffDate(cutoffDate)
                    .entityTypes(entityTypes != null && !entityTypes.isEmpty() ? entityTypes : Set.of("AV_DATA"))
                    .excludedClientIds(excludedClientIds)
                    .batchSize(batchSize)
                    .dryRun(true)
                    .initiatedBy("user")
                    .build();

            DataCleanupPreviewDto preview = cleanupService.previewCleanup(request);
            return ResponseEntity.ok(preview);

        } catch (IllegalArgumentException e) {
            log.warn("Ошибка валидации при предпросмотре: {}", e.getMessage());
            return ResponseEntity.badRequest().body(DataCleanupPreviewDto.builder()
                    .warnings(List.of(e.getMessage()))
                    .build());
        } catch (Exception e) {
            log.error("Ошибка при предпросмотре очистки", e);
            return ResponseEntity.internalServerError().body(DataCleanupPreviewDto.builder()
                    .warnings(List.of("Ошибка: " + e.getMessage()))
                    .build());
        }
    }

    /**
     * Выполнение очистки данных (асинхронно)
     * Контроллер немедленно возвращает operationId для WebSocket подключения
     */
    @PostMapping("/execute")
    @ResponseBody
    public ResponseEntity<DataCleanupResultDto> executeCleanup(
            @RequestParam("cutoffDate") String cutoffDateStr,
            @RequestParam(value = "entityTypes", required = false) Set<String> entityTypes,
            @RequestParam(value = "excludedClientIds", required = false) Set<Long> excludedClientIds,
            @RequestParam(value = "batchSize", defaultValue = "10000") int batchSize,
            @RequestParam(value = "confirmation", required = true) String confirmation) {

        log.info("Запрос на выполнение ASYNC очистки: дата={}, типы={}, исключения={}",
                cutoffDateStr, entityTypes, excludedClientIds);

        // Проверка подтверждения
        if (!"CONFIRM".equals(confirmation)) {
            log.warn("Попытка очистки без подтверждения");
            return ResponseEntity.badRequest().body(DataCleanupResultDto.builder()
                    .success(false)
                    .errorMessage("Требуется ввести CONFIRM для подтверждения операции")
                    .build());
        }

        try {
            LocalDateTime cutoffDate = LocalDateTime.parse(cutoffDateStr, DATE_TIME_FORMATTER);

            // Генерируем уникальный ID ДО запуска async операции
            String operationId = UUID.randomUUID().toString();

            DataCleanupRequestDto request = DataCleanupRequestDto.builder()
                    .cutoffDate(cutoffDate)
                    .entityTypes(entityTypes != null && !entityTypes.isEmpty() ? entityTypes : Set.of("AV_DATA"))
                    .excludedClientIds(excludedClientIds)
                    .batchSize(batchSize)
                    .dryRun(false)
                    .initiatedBy("user")
                    .operationId(operationId)
                    .build();

            log.info("Запуск ASYNC очистки с operationId: {}", operationId);

            // ✅ ЗАПУСКАЕМ ASYNC - контроллер НЕ ЖДЁТ завершения
            cleanupService.executeCleanupAsync(request);

            // ✅ СРАЗУ ВОЗВРАЩАЕМ operationId для WebSocket подключения
            DataCleanupResultDto result = DataCleanupResultDto.builder()
                    .operationId(operationId)
                    .success(true) // операция ЗАПУЩЕНА (не завершена!)
                    .build();

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("Ошибка валидации при очистке: {}", e.getMessage());
            return ResponseEntity.badRequest().body(DataCleanupResultDto.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("Ошибка при запуске очистки", e);
            return ResponseEntity.internalServerError().body(DataCleanupResultDto.builder()
                    .success(false)
                    .errorMessage("Ошибка: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Получение истории очисток
     */
    @GetMapping("/history")
    @ResponseBody
    public ResponseEntity<List<DataCleanupHistory>> getHistory(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {

        log.debug("Запрос истории очисток, лимит: {}", limit);
        List<DataCleanupHistory> history = cleanupService.getCleanupHistory(limit);
        return ResponseEntity.ok(history);
    }

    /**
     * Получение настроек очистки
     */
    @GetMapping("/settings")
    @ResponseBody
    public ResponseEntity<List<DataCleanupSettingsDto>> getSettings() {
        log.debug("Запрос настроек очистки");
        List<DataCleanupSettingsDto> settings = cleanupService.getCleanupSettings();
        return ResponseEntity.ok(settings);
    }

    /**
     * Обновление настроек очистки
     */
    @PutMapping("/settings")
    @ResponseBody
    public ResponseEntity<DataCleanupSettingsDto> updateSettings(
            @RequestBody DataCleanupSettingsDto settings) {

        log.info("Обновление настроек очистки для типа: {}", settings.getEntityType());

        try {
            DataCleanupSettingsDto updated = cleanupService.updateCleanupSettings(settings);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Ошибка при обновлении настроек: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Ошибка при обновлении настроек", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
