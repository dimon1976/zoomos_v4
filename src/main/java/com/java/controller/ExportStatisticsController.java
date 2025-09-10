// src/main/java/com/java/controller/ExportStatisticsController.java
package com.java.controller;

import com.java.dto.StatisticsComparisonDto;
import com.java.dto.StatisticsFilterDto;
import com.java.dto.StatisticsFilteredResponseDto;
import com.java.dto.StatisticsRequestDto;
import com.java.model.entity.ExportSession;
import com.java.repository.ExportSessionRepository;
import com.java.repository.ExportStatisticsRepository;
import com.java.repository.ExportTemplateRepository;
import com.java.service.exports.ExportStatisticsWriterService;
import com.java.service.statistics.ExportStatisticsService;
import com.java.service.statistics.StatisticsSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Контроллер для работы со статистикой экспорта
 */
@Controller
@RequestMapping("/statistics")
@RequiredArgsConstructor
@Slf4j
public class ExportStatisticsController {

    private final ExportStatisticsService statisticsService;
    private final StatisticsSettingsService settingsService;
    private final ExportSessionRepository sessionRepository;
    private final ExportTemplateRepository templateRepository;
    private final ExportStatisticsRepository statisticsRepository;
    private final ExportStatisticsWriterService writerService;

    /**
     * Страница выбора операций для анализа статистики
     */
    @GetMapping("/client/{clientId}")
    public String showStatisticsSetup(@PathVariable Long clientId, Model model) {
        log.debug("GET запрос на настройку статистики для клиента ID: {}", clientId);

        // Получаем последние экспорты клиента с загруженными данными fileOperation
        Page<ExportSession> recentExports = sessionRepository.findByClientIdWithTemplate(
                clientId,
                PageRequest.of(0, settingsService.getMaxOperations(),
                        Sort.by(Sort.Direction.DESC, "startedAt"))
        );

        // Получаем шаблоны клиента с включенной статистикой
        var client = new com.java.model.Client();
        client.setId(clientId);
        var templates = templateRepository.findByClientAndIsActiveTrue(client).stream()
                .filter(template -> Boolean.TRUE.equals(template.getEnableStatistics()))
                .toList();

        model.addAttribute("clientId", clientId);
        model.addAttribute("recentExports", recentExports.getContent());
        model.addAttribute("templates", templates);
        model.addAttribute("maxOperations", settingsService.getMaxOperations());
        model.addAttribute("warningPercentage", settingsService.getWarningPercentage());
        model.addAttribute("criticalPercentage", settingsService.getCriticalPercentage());

        return "statistics/setup";
    }

    /**
     * Запуск анализа статистики
     */
    @PostMapping("/analyze")
    public String analyzeStatistics(@ModelAttribute StatisticsRequestDto request,
                                    RedirectAttributes redirectAttributes,
                                    @RequestParam Long clientId,
                                    Model model) {
        log.debug("POST запрос на анализ статистики: {}", request);

        try {
            // Вычисляем статистику
            List<StatisticsComparisonDto> comparison = statisticsService.calculateComparison(request);

            if (comparison.isEmpty()) {
                redirectAttributes.addFlashAttribute("warningMessage",
                        "Нет данных для анализа статистики");
                return "redirect:/statistics/client/" + clientId;
            }

            // Добавляем данные в модель
            model.addAttribute("comparison", comparison);
            model.addAttribute("clientId", clientId);
            model.addAttribute("request", request);
            model.addAttribute("warningPercentage",
                    request.getWarningPercentage() != null ? request.getWarningPercentage() : settingsService.getWarningPercentage());
            model.addAttribute("criticalPercentage",
                    request.getCriticalPercentage() != null ? request.getCriticalPercentage() : settingsService.getCriticalPercentage());

            return "statistics/results";

        } catch (Exception e) {
            log.error("Ошибка анализа статистики", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка анализа статистики: " + e.getMessage());

            if (clientId != null) {
                return "redirect:/statistics/client/" + clientId;
            } else {
                return "redirect:/clients";
            }
        }
    }

    /**
     * API endpoint для получения данных статистики в JSON формате
     */
    @PostMapping("/api/analyze")
    @ResponseBody
    public List<StatisticsComparisonDto> analyzeStatisticsApi(@RequestBody StatisticsRequestDto request) {
        log.debug("API запрос на анализ статистики: {}", request);
        return statisticsService.calculateComparison(request);
    }

    /**
     * Страница настроек статистики (глобальные настройки)
     */
    @GetMapping("/settings")
    public String showSettings(Model model) {
        log.debug("GET запрос на страницу настроек статистики");

        Map<String, String> settings = settingsService.getAllSettings();
        model.addAttribute("settings", settings);

        return "statistics/settings";
    }

    /**
     * Обновление настроек статистики
     */
    @PostMapping("/settings/update")
    public String updateSettings(@RequestParam Map<String, String> params,
                                 RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на обновление настроек статистики: {}", params);

        try {
            // Обновляем только разрешенные настройки
            String[] allowedSettings = {
                    "deviation_percentage_warning",
                    "deviation_percentage_critical",
                    "statistics_max_operations"
            };

            for (String setting : allowedSettings) {
                if (params.containsKey(setting)) {
                    String value = params.get(setting);

                    // Валидация для числовых настроек
                    if (setting.contains("percentage") || setting.contains("max")) {
                        try {
                            int intValue = Integer.parseInt(value);
                            if (intValue < 0 || intValue > 100) {
                                throw new IllegalArgumentException("Значение должно быть от 0 до 100");
                            }
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Некорректное числовое значение: " + value);
                        }
                    }

                    settingsService.updateSetting(setting, value);
                }
            }

            redirectAttributes.addFlashAttribute("successMessage", "Настройки успешно обновлены");

        } catch (Exception e) {
            log.error("Ошибка обновления настроек", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка обновления настроек: " + e.getMessage());
        }

        return "redirect:/statistics/settings";
    }

    /**
     * Получает сохранённую статистику для сессии экспорта
     */
    @GetMapping("/session/{sessionId}/saved")
    @ResponseBody
    public Map<String, Object> getSavedStatistics(@PathVariable Long sessionId) {
        log.debug("GET запрос на получение сохранённой статистики для сессии ID: {}", sessionId);

        try {
            var statistics = statisticsRepository.findByExportSessionId(sessionId);

            return Map.of(
                    "success", true,
                    "sessionId", sessionId,
                    "count", statistics.size(),
                    "hasStatistics", !statistics.isEmpty(),
                    "message", statistics.isEmpty() ?
                            "Нет сохранённой статистики для данной сессии" :
                            "Найдено " + statistics.size() + " записей статистики"
            );
        } catch (Exception e) {
            log.error("Ошибка получения сохранённой статистики", e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }


    /**
     * Диагностический endpoint для проверки данных
     */
    @GetMapping("/debug-data/client/{clientId}")
    @ResponseBody
    public Map<String, Object> debugData(@PathVariable Long clientId) {
        log.debug("Диагностика данных для клиента ID: {}", clientId);

        Page<ExportSession> recentExports = sessionRepository.findByClientIdWithTemplate(
                clientId,
                PageRequest.of(0, settingsService.getMaxOperations(),
                        Sort.by(Sort.Direction.DESC, "startedAt"))
        );

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("exportsCount", recentExports.getContent().size());
        
        for (ExportSession export : recentExports.getContent()) {
            if (export.getFileOperation().getId() == 258L) {
                result.put("export258_sessionId", export.getId());
                result.put("export258_sessionStarted", export.getStartedAt());
                result.put("export258_sessionStarted_formatted", 
                    export.getStartedAt().toString());
                result.put("export258_fileOpId", export.getFileOperation().getId());
                result.put("export258_fileOpStarted", export.getFileOperation().getStartedAt());
                result.put("export258_fileOpStarted_formatted", 
                    export.getFileOperation().getStartedAt().toString());
                
                // Добавим системную информацию
                result.put("serverTimeZone", java.util.TimeZone.getDefault().getID());
                result.put("currentServerTime", java.time.Instant.now().toString());
                
                // Проверим разницу во времени
                long timeDifference = export.getStartedAt().toInstant().toEpochMilli() - 
                                    export.getFileOperation().getStartedAt().toInstant().toEpochMilli();
                result.put("timeDifferenceMs", timeDifference);
                
                break;
            }
        }
        
        return result;
    }

    /**
     * AJAX endpoint для предварительного просмотра статистики
     */
    @PostMapping("/preview")
    @ResponseBody
    public Map<String, Object> previewStatistics(@RequestBody StatisticsRequestDto request) {
        log.debug("AJAX запрос на предварительный просмотр статистики");

        try {
            List<StatisticsComparisonDto> comparison = statisticsService.calculateComparison(request);

            // Возвращаем краткую статистику
            int totalGroups = comparison.size();
            int totalOperations = comparison.stream()
                    .mapToInt(c -> c.getOperations().size())
                    .max().orElse(0);

            long criticalChanges = comparison.stream()
                    .flatMap(c -> c.getOperations().stream())
                    .flatMap(op -> op.getMetrics().values().stream())
                    .filter(metric -> metric.getAlertLevel() == StatisticsComparisonDto.AlertLevel.CRITICAL)
                    .count();

            return Map.of(
                    "success", true,
                    "totalGroups", totalGroups,
                    "totalOperations", totalOperations,
                    "criticalChanges", criticalChanges,
                    "hasData", !comparison.isEmpty()
            );

        } catch (Exception e) {
            log.error("Ошибка предварительного просмотра", e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    // === New Filtering API Endpoints for Iteration 4 ===

    /**
     * API endpoint для получения отфильтрованной статистики с пагинацией
     */
    @PostMapping("/api/filtered-analyze")
    @ResponseBody
    public StatisticsFilteredResponseDto analyzeFilteredStatistics(@Valid @RequestBody StatisticsFilterDto filterDto) {
        log.debug("API запрос на отфильтрованный анализ статистики для клиента: {}", filterDto.getClientId());
        
        try {
            return statisticsService.calculateFilteredComparison(filterDto);
        } catch (Exception e) {
            log.error("Ошибка отфильтрованного анализа статистики для клиента {}", filterDto.getClientId(), e);
            
            // Возвращаем пустой результат с ошибкой в aggregatedStats
            return StatisticsFilteredResponseDto.builder()
                    .results(List.of())
                    .totalElements(0L)
                    .totalPages(0)
                    .currentPage(filterDto.getPage())
                    .pageSize(filterDto.getSize())
                    .hasNext(false)
                    .hasPrevious(false)
                    .appliedFilters(filterDto)
                    .aggregatedStats(Map.of("error", e.getMessage()))
                    .availableFieldValues(Map.of())
                    .fieldMetadata(Map.of())
                    .build();
        }
    }

    /**
     * API endpoint для получения доступных значений полей для динамических фильтров
     */
    @GetMapping("/api/field-values/{clientId}")
    @ResponseBody
    public Map<String, Object> getFieldValues(@PathVariable Long clientId,
                                              @RequestParam(required = false) List<Long> sessionIds) {
        log.debug("API запрос на получение значений полей для клиента: {}", clientId);
        
        try {
            Map<String, List<String>> availableValues = statisticsService.getAvailableFieldValues(clientId, sessionIds);
            
            return Map.of(
                    "success", true,
                    "clientId", clientId,
                    "availableValues", availableValues,
                    "sessionIds", sessionIds != null ? sessionIds : List.of()
            );
            
        } catch (Exception e) {
            log.error("Ошибка получения значений полей для клиента {}", clientId, e);
            
            return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "clientId", clientId
            );
        }
    }

    /**
     * API endpoint для быстрой проверки наличия данных для фильтрации
     */
    @GetMapping("/api/filter-availability/{clientId}")
    @ResponseBody
    public Map<String, Object> checkFilterAvailability(@PathVariable Long clientId,
                                                       @RequestParam(required = false, defaultValue = "30") int daysBack) {
        log.debug("Проверка доступности фильтров для клиента: {} за {} дней", clientId, daysBack);
        
        try {
            // Получаем статистики за период
            java.time.ZonedDateTime sinceDate = java.time.ZonedDateTime.now().minusDays(daysBack);
            List<com.java.model.entity.ExportStatistics> statistics = 
                    statisticsService.getStatisticsForDateRange(clientId, sinceDate, java.time.ZonedDateTime.now());
            
            boolean hasData = !statistics.isEmpty();
            int totalRecords = statistics.size();
            
            // Подсчитываем уникальные группы и поля
            long uniqueGroups = statistics.stream()
                    .map(com.java.model.entity.ExportStatistics::getGroupFieldValue)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
            
            long uniqueFields = statistics.stream()
                    .map(com.java.model.entity.ExportStatistics::getCountFieldName)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
            
            return Map.of(
                    "success", true,
                    "hasData", hasData,
                    "totalRecords", totalRecords,
                    "uniqueGroups", uniqueGroups,
                    "uniqueFields", uniqueFields,
                    "daysBack", daysBack,
                    "period", sinceDate.toString() + " - " + java.time.ZonedDateTime.now().toString(),
                    "recommendFiltering", hasData && uniqueGroups > 5 // Рекомендуем фильтры если много групп
            );
            
        } catch (Exception e) {
            log.error("Ошибка проверки доступности фильтров для клиента {}", clientId, e);
            
            return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "hasData", false
            );
        }
    }

    /**
     * API endpoint для получения сводной информации о статистике клиента (dashboard)
     */
    @GetMapping("/api/summary/{clientId}")
    @ResponseBody
    public Map<String, Object> getStatisticsSummary(@PathVariable Long clientId,
                                                    @RequestParam(required = false, defaultValue = "7") int daysBack) {
        log.debug("API запрос на сводку статистики для клиента: {} за {} дней", clientId, daysBack);
        
        try {
            return statisticsService.getStatisticsSummary(clientId, daysBack);
            
        } catch (Exception e) {
            log.error("Ошибка получения сводки статистики для клиента {}", clientId, e);
            
            return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "clientId", clientId
            );
        }
    }

    /**
     * API endpoint для проверки здоровья системы статистики клиента
     */
    @GetMapping("/api/health/{clientId}")
    @ResponseBody
    public Map<String, Object> getStatisticsHealth(@PathVariable Long clientId) {
        log.debug("API запрос на проверку здоровья статистики для клиента: {}", clientId);
        
        return statisticsService.getStatisticsHealthCheck(clientId);
    }

    /**
     * Экспорт отфильтрованной статистики в Excel
     */
    @PostMapping("/export/excel")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> exportStatisticsToExcel(
            @Valid @RequestBody StatisticsFilterDto filterDto) {
        log.debug("Запрос на экспорт отфильтрованной статистики в Excel для клиента: {}", filterDto.getClientId());
        
        try {
            // Получаем отфильтрованные данные без пагинации
            StatisticsFilterDto exportFilter = StatisticsFilterDto.builder()
                    .clientId(filterDto.getClientId())
                    .exportSessionIds(filterDto.getExportSessionIds())
                    .alertLevels(filterDto.getAlertLevels())
                    .groupFieldFilters(filterDto.getGroupFieldFilters())
                    .countFieldFilters(filterDto.getCountFieldFilters())
                    .minChangePercentage(filterDto.getMinChangePercentage())
                    .maxChangePercentage(filterDto.getMaxChangePercentage())
                    .hideNoChanges(filterDto.getHideNoChanges())
                    .onlyWarnings(filterDto.getOnlyWarnings())
                    .onlyProblems(filterDto.getOnlyProblems())
                    .page(0)
                    .size(10000) // Экспортируем максимально много записей
                    .build();

            StatisticsFilteredResponseDto response = statisticsService.calculateFilteredComparison(exportFilter);
            
            if (response.getResults().isEmpty()) {
                return org.springframework.http.ResponseEntity.badRequest()
                        .body(null);
            }

            // Формируем данные для Excel
            List<Map<String, Object>> excelData = convertStatisticsToExcelFormat(response);
            
            // Генерируем файл Excel
            String fileName = "statistics_export_" + System.currentTimeMillis() + ".xlsx";
            byte[] excelBytes = generateExcelFile(excelData, response, fileName);
            
            // Возвращаем файл
            org.springframework.core.io.ByteArrayResource resource = 
                    new org.springframework.core.io.ByteArrayResource(excelBytes);
            
            return org.springframework.http.ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + fileName + "\"")
                    .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, 
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("Ошибка экспорта статистики в Excel для клиента {}", filterDto.getClientId(), e);
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Преобразует данные статистики в формат для Excel
     */
    private List<Map<String, Object>> convertStatisticsToExcelFormat(StatisticsFilteredResponseDto response) {
        List<Map<String, Object>> excelData = new java.util.ArrayList<>();
        
        for (StatisticsComparisonDto group : response.getResults()) {
            for (StatisticsComparisonDto.OperationStatistics operation : group.getOperations()) {
                for (Map.Entry<String, StatisticsComparisonDto.MetricValue> metricEntry : operation.getMetrics().entrySet()) {
                    Map<String, Object> row = new java.util.HashMap<>();
                    
                    row.put("Группа", group.getGroupFieldValue());
                    row.put("Операция", operation.getOperationName());
                    row.put("Дата экспорта", operation.getExportDate());
                    row.put("Метрика", metricEntry.getKey());
                    row.put("Текущее значение", metricEntry.getValue().getCurrentValue());
                    row.put("Предыдущее значение", metricEntry.getValue().getPreviousValue());
                    row.put("Изменение %", metricEntry.getValue().getChangePercentage());
                    row.put("Тип изменения", metricEntry.getValue().getChangeType().name());
                    row.put("Уровень предупреждения", metricEntry.getValue().getAlertLevel().name());
                    
                    excelData.add(row);
                }
            }
        }
        
        return excelData;
    }

    /**
     * Генерирует Excel файл с информацией о примененных фильтрах
     */
    private byte[] generateExcelFile(List<Map<String, Object>> data, 
                                   StatisticsFilteredResponseDto response, 
                                   String fileName) throws Exception {
        
        // Используем Apache POI для генерации Excel
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Статистика");
        
        int currentRow = 0;
        
        // Добавляем информацию о примененных фильтрах
        currentRow = addFilterInfoToSheet(sheet, response, currentRow);
        
        // Пропускаем строку
        currentRow += 2;
        
        // Создаем заголовки данных
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(currentRow);
        String[] headers = {"Группа", "Операция", "Дата экспорта", "Метрика", 
                           "Текущее значение", "Предыдущее значение", "Изменение %", 
                           "Тип изменения", "Уровень предупреждения"};
        
        // Стиль заголовков
        org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        currentRow++;
        
        // Заполняем данными
        for (Map<String, Object> rowData : data) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(currentRow++);
            
            int cellNum = 0;
            for (String header : headers) {
                org.apache.poi.ss.usermodel.Cell cell = row.createCell(cellNum++);
                Object value = rowData.get(header);
                if (value != null) {
                    if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                    } else if (value instanceof java.time.ZonedDateTime) {
                        // Форматируем дату
                        java.time.ZonedDateTime dateTime = (java.time.ZonedDateTime) value;
                        cell.setCellValue(dateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
                    } else {
                        cell.setCellValue(value.toString());
                    }
                }
            }
        }
        
        // Автоподбор ширины колонок
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        // Конвертируем в byte array
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        return outputStream.toByteArray();
    }

    /**
     * Добавляет информацию о примененных фильтрах в Excel лист
     */
    private int addFilterInfoToSheet(org.apache.poi.ss.usermodel.Sheet sheet, 
                                   StatisticsFilteredResponseDto response, 
                                   int startRow) {
        
        StatisticsFilterDto filters = response.getAppliedFilters();
        int currentRow = startRow;
        
        // Стиль для заголовка фильтров
        org.apache.poi.ss.usermodel.CellStyle titleStyle = sheet.getWorkbook().createCellStyle();
        org.apache.poi.ss.usermodel.Font titleFont = sheet.getWorkbook().createFont();
        titleFont.setBold(true);
        titleFont.setFontHeight((short) 280); // 14pt
        titleStyle.setFont(titleFont);
        
        // Заголовок
        org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(currentRow++);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Информация об экспорте и примененных фильтрах");
        titleCell.setCellStyle(titleStyle);
        
        // Пропускаем строку
        currentRow++;
        
        // Информация об экспорте
        addInfoRow(sheet, currentRow++, "Дата экспорта:", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
        addInfoRow(sheet, currentRow++, "ID клиента:", filters.getClientId().toString());
        addInfoRow(sheet, currentRow++, "Всего записей найдено:", String.valueOf(response.getTotalElements()));
        addInfoRow(sheet, currentRow++, "Записей экспортировано:", String.valueOf(response.getResults().size()));
        
        // Пропускаем строку
        currentRow++;
        
        // Информация о фильтрах
        org.apache.poi.ss.usermodel.Row filterTitleRow = sheet.createRow(currentRow++);
        org.apache.poi.ss.usermodel.Cell filterTitleCell = filterTitleRow.createCell(0);
        filterTitleCell.setCellValue("Примененные фильтры:");
        filterTitleCell.setCellStyle(titleStyle);
        
        // Уровни предупреждений
        if (!filters.getAlertLevels().isEmpty()) {
            addInfoRow(sheet, currentRow++, "Уровни предупреждений:", String.join(", ", filters.getAlertLevels()));
        }
        
        // Диапазон изменений
        if (filters.getMinChangePercentage() != null || filters.getMaxChangePercentage() != null) {
            String range = "";
            if (filters.getMinChangePercentage() != null) {
                range += "от " + filters.getMinChangePercentage() + "%";
            }
            if (filters.getMaxChangePercentage() != null) {
                if (!range.isEmpty()) range += " ";
                range += "до " + filters.getMaxChangePercentage() + "%";
            }
            addInfoRow(sheet, currentRow++, "Диапазон изменений:", range);
        }
        
        // Дополнительные условия
        if (filters.getHideNoChanges()) {
            addInfoRow(sheet, currentRow++, "Скрыть без изменений:", "Да");
        }
        if (filters.getOnlyWarnings()) {
            addInfoRow(sheet, currentRow++, "Только предупреждения:", "Да");
        }
        if (filters.getOnlyProblems()) {
            addInfoRow(sheet, currentRow++, "Только проблемы:", "Да");
        }
        
        // Фильтры по группам полей
        if (!filters.getGroupFieldFilters().isEmpty()) {
            StringBuilder groupFilters = new StringBuilder();
            for (Map.Entry<String, List<String>> entry : filters.getGroupFieldFilters().entrySet()) {
                if (groupFilters.length() > 0) groupFilters.append("; ");
                groupFilters.append(entry.getKey()).append(": ").append(String.join(", ", entry.getValue()));
            }
            addInfoRow(sheet, currentRow++, "Фильтры по группам:", groupFilters.toString());
        }
        
        // Агрегированная статистика
        if (response.getAggregatedStats() != null && !response.getAggregatedStats().isEmpty()) {
            currentRow++;
            org.apache.poi.ss.usermodel.Row statsTitle = sheet.createRow(currentRow++);
            org.apache.poi.ss.usermodel.Cell statsTitleCell = statsTitle.createCell(0);
            statsTitleCell.setCellValue("Сводная статистика:");
            statsTitleCell.setCellStyle(titleStyle);
            
            for (Map.Entry<String, Object> stat : response.getAggregatedStats().entrySet()) {
                String key = formatStatKey(stat.getKey());
                addInfoRow(sheet, currentRow++, key + ":", stat.getValue().toString());
            }
        }
        
        return currentRow;
    }
    
    /**
     * Добавляет информационную строку в Excel лист
     */
    private void addInfoRow(org.apache.poi.ss.usermodel.Sheet sheet, int rowNum, String label, String value) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum);
        
        // Стиль для подписи
        org.apache.poi.ss.usermodel.CellStyle labelStyle = sheet.getWorkbook().createCellStyle();
        org.apache.poi.ss.usermodel.Font labelFont = sheet.getWorkbook().createFont();
        labelFont.setBold(true);
        labelStyle.setFont(labelFont);
        
        org.apache.poi.ss.usermodel.Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);
        
        org.apache.poi.ss.usermodel.Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
    }
    
    /**
     * Форматирует ключи статистики для отображения
     */
    private String formatStatKey(String key) {
        switch (key) {
            case "total_operations": return "Всего операций";
            case "total_groups": return "Всего групп";
            case "total_records": return "Всего записей";
            case "normalCount": return "Нормальных изменений";
            case "warningCount": return "Предупреждений";
            case "criticalCount": return "Критических изменений";
            case "problemsCount": return "Всего проблем";
            default: return key;
        }
    }

}