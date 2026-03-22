package com.java.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.config.*;
import com.java.service.maintenance.ConfigExportService;
import com.java.service.maintenance.ConfigImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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
    public ResponseEntity<byte[]> exportConfig(ConfigExportOptionsDto options) {
        log.info("Запрос на экспорт конфигурации");
        try {
            ConfigExportDto dto = exportService.exportConfig(options);
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(dto);

            String filename = "zoomos-config-" + LocalDate.now() + ".json";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename).build().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json);
        } catch (Exception e) {
            log.error("Ошибка при экспорте конфигурации", e);
            return ResponseEntity.internalServerError()
                    .body(("Ошибка экспорта: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
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
