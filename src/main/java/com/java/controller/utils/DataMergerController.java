package com.java.controller.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.utils.DataMergerConfigDto;
import com.java.dto.utils.MergedProductDto;
import com.java.exception.DataMergerException;
import com.java.service.utils.DataMergerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Контроллер для утилиты объединения данных товаров с аналогами и ссылками
 */
@Controller
@RequestMapping("/utils/data-merger")
@RequiredArgsConstructor
@Slf4j
public class DataMergerController {

    private final DataMergerService dataMergerService;
    private final ObjectMapper objectMapper;

    /**
     * Отображение формы загрузки файлов
     */
    @GetMapping
    public String showForm(Model model) {
        log.info("Opening Data Merger utility");

        model.addAttribute("pageTitle", "Data Merger - Объединение данных");
        model.addAttribute("utilityInfo", "Утилита для объединения товаров-оригиналов с аналогами и ссылками");

        return "utils/data-merger";
    }

    /**
     * Отображение страницы документации
     */
    @GetMapping("/docs")
    public String showDocumentation() {
        log.info("Opening Data Merger documentation");
        return "utils/data-merger-docs";
    }

    /**
     * Обработка загруженных файлов - пока просто проверка
     */
    @PostMapping("/upload")
    public String uploadFiles(
            @RequestParam("sourceFile") MultipartFile sourceFile,
            @RequestParam("linksFile") MultipartFile linksFile,
            RedirectAttributes redirectAttributes) {

        log.info("Received files: source={} ({}), links={} ({})",
                sourceFile.getOriginalFilename(),
                sourceFile.getSize(),
                linksFile.getOriginalFilename(),
                linksFile.getSize());

        // Проверка что файлы не пустые
        if (sourceFile.isEmpty() || linksFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Файлы не должны быть пустыми");
            return "redirect:/utils/data-merger";
        }

        // Пока просто показываем что файлы получены
        String message = String.format("Файлы получены: %s (%.2f KB), %s (%.2f KB)",
                sourceFile.getOriginalFilename(),
                sourceFile.getSize() / 1024.0,
                linksFile.getOriginalFilename(),
                linksFile.getSize() / 1024.0);

        redirectAttributes.addFlashAttribute("message", message);

        return "redirect:/utils/data-merger";
    }

    /**
     * Обработка файлов и возврат результата в выбранном формате
     */
    @PostMapping("/process")
    public ResponseEntity<byte[]> processFiles(
            @RequestParam("sourceFile") MultipartFile sourceFile,
            @RequestParam("linksFile") MultipartFile linksFile,
            @RequestParam(value = "format", defaultValue = "csv") String format) {

        log.info("Processing files: source={}, links={}, format={}",
                sourceFile.getOriginalFilename(),
                linksFile.getOriginalFilename(),
                format);

        try {
            // Проверка файлов
            if (sourceFile.isEmpty() || linksFile.isEmpty()) {
                throw new DataMergerException("Файлы не должны быть пустыми");
            }

            // Обработка через сервис
            List<MergedProductDto> result = dataMergerService.processFiles(sourceFile, linksFile);

            // Генерация файла в нужном формате
            byte[] fileBytes;
            String fileName;
            MediaType contentType;

            switch (format.toLowerCase()) {
                case "excel", "xlsx":
                    fileBytes = dataMergerService.generateExcelFile(result);
                    fileName = "merged_data.xlsx";
                    contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    break;
                case "csv":
                default:
                    fileBytes = dataMergerService.generateCsvFile(result);
                    fileName = "merged_data.csv";
                    contentType = MediaType.parseMediaType("text/csv");
                    break;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
            headers.setContentType(contentType);
            headers.setContentLength(fileBytes.length);

            return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);

        } catch (DataMergerException e) {
            log.error("Data merger error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing files", e);
            throw new DataMergerException("Неожиданная ошибка при обработке файлов: " + e.getMessage(), e);
        }
    }

    /**
     * Анализ заголовков файлов для настройки маппинга
     */
    @PostMapping("/analyze")
    public ResponseEntity<DataMergerConfigDto> analyzeFiles(
            @RequestParam("sourceFile") MultipartFile sourceFile,
            @RequestParam("linksFile") MultipartFile linksFile) {

        log.info("Analyzing files for headers: source={}, links={}",
                sourceFile.getOriginalFilename(),
                linksFile.getOriginalFilename());

        try {
            // Проверка файлов
            if (sourceFile.isEmpty() || linksFile.isEmpty()) {
                throw new DataMergerException("Файлы не должны быть пустыми");
            }

            // Анализ заголовков через сервис
            DataMergerConfigDto config = dataMergerService.analyzeHeaders(sourceFile, linksFile);

            return ResponseEntity.ok(config);

        } catch (DataMergerException e) {
            log.error("Data merger analysis error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error analyzing files", e);
            throw new DataMergerException("Неожиданная ошибка при анализе файлов: " + e.getMessage(), e);
        }
    }

    /**
     * Обработка файлов с пользовательским маппингом
     */
    @PostMapping("/process-with-mapping")
    public ResponseEntity<byte[]> processFilesWithMapping(
            @RequestParam("sourceFile") MultipartFile sourceFile,
            @RequestParam("linksFile") MultipartFile linksFile,
            @RequestParam("configJson") String configJson,
            @RequestParam(value = "format", defaultValue = "csv") String format) {

        log.info("Processing files with mapping: source={}, links={}, format={}",
                sourceFile.getOriginalFilename(),
                linksFile.getOriginalFilename(),
                format);

        try {
            // Проверка файлов
            if (sourceFile.isEmpty() || linksFile.isEmpty()) {
                throw new DataMergerException("Файлы не должны быть пустыми");
            }

            // Парсим конфигурацию маппинга
            DataMergerConfigDto config = objectMapper.readValue(configJson, DataMergerConfigDto.class);

            // Обработка через сервис с маппингом
            List<MergedProductDto> result = dataMergerService.processFiles(sourceFile, linksFile, config);

            // Генерация файла в нужном формате
            byte[] fileBytes;
            String fileName;
            MediaType contentType;

            switch (format.toLowerCase()) {
                case "excel", "xlsx":
                    fileBytes = dataMergerService.generateExcelFile(result);
                    fileName = "merged_data.xlsx";
                    contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    break;
                case "csv":
                default:
                    fileBytes = dataMergerService.generateCsvFile(result);
                    fileName = "merged_data.csv";
                    contentType = MediaType.parseMediaType("text/csv");
                    break;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
            headers.setContentType(contentType);
            headers.setContentLength(fileBytes.length);

            return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);

        } catch (DataMergerException e) {
            log.error("Data merger error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing files with mapping", e);
            throw new DataMergerException("Неожиданная ошибка при обработке файлов: " + e.getMessage(), e);
        }
    }
}