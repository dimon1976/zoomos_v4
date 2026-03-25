package com.java.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.config.*;
import com.java.service.maintenance.ConfigExportService;
import com.java.service.maintenance.ConfigImportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Контроллер для экспорта и импорта конфигурации Zoomos v4.
 * Позволяет переносить настройки между серверами (dev → prod и обратно).
 */
@Controller
@RequestMapping("/maintenance/config")
@RequiredArgsConstructor
@Slf4j
public class ConfigImportExportController {

    private final ConfigExportService exportService;
    private final ConfigImportService importService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String configPage(Model model) {
        model.addAttribute("exportOptions", new ConfigExportOptionsDto());
        return "maintenance/config-import-export";
    }

    /**
     * Скачать конфигурацию в виде JSON-файла.
     */
    @PostMapping("/export")
    public void exportConfig(ConfigExportOptionsDto options, HttpServletResponse response) throws IOException {
        log.info("Запрос на экспорт конфигурации");
        try {
            ConfigExportDto dto = exportService.exportConfig(options);
            String filename = "zoomos-config-" + LocalDate.now() + ".json";
            response.setContentType("application/json; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(response.getOutputStream(), dto);
        } catch (Exception e) {
            log.error("Ошибка при экспорте конфигурации", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Ошибка экспорта: " + e.getMessage());
        }
    }

    /**
     * Анализ загруженного JSON-файла — предварительный просмотр без сохранения.
     */
    @PostMapping("/preview")
    @ResponseBody
    public ResponseEntity<ConfigImportPreviewDto> previewImport(
            @RequestParam("file") MultipartFile file,
            ConfigExportOptionsDto options) {

        log.info("Запрос превью импорта, файл: {}, размер: {} байт", file.getOriginalFilename(), file.getSize());
        String previewFilename = file.getOriginalFilename();
        if (previewFilename == null || !previewFilename.toLowerCase().endsWith(".json")) {
            log.warn("Неверный тип файла для превью: {}", previewFilename);
            return ResponseEntity.badRequest().build();
        }
        try {
            ConfigExportDto config = objectMapper.readValue(file.getBytes(), ConfigExportDto.class);
            ConfigImportPreviewDto preview = importService.preview(config, options);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            log.error("Ошибка при разборе файла импорта", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Выполнить импорт из загруженного JSON-файла.
     */
    @PostMapping("/import")
    @ResponseBody
    public ResponseEntity<ConfigImportResultDto> executeImport(
            @RequestParam("file") MultipartFile file,
            ConfigExportOptionsDto options) {

        log.info("Запрос на импорт конфигурации, файл: {}", file.getOriginalFilename());
        String importFilename = file.getOriginalFilename();
        if (importFilename == null || !importFilename.toLowerCase().endsWith(".json")) {
            log.warn("Неверный тип файла для импорта: {}", importFilename);
            ConfigImportResultDto error = new ConfigImportResultDto();
            error.setSuccess(false);
            error.getErrors().add("Ожидается файл формата .json");
            return ResponseEntity.badRequest().body(error);
        }
        try {
            ConfigExportDto config = objectMapper.readValue(file.getBytes(), ConfigExportDto.class);
            ConfigImportResultDto result = importService.execute(config, options);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка при импорте конфигурации", e);
            ConfigImportResultDto error = new ConfigImportResultDto();
            error.setSuccess(false);
            error.getErrors().add("Ошибка разбора файла: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
