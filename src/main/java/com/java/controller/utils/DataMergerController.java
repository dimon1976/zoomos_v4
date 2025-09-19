package com.java.controller.utils;

import com.java.dto.utils.MergedDataRow;
import com.java.service.utils.DataMergerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/utils/data-merger")
@RequiredArgsConstructor
@Slf4j
public class DataMergerController {

    private final DataMergerService dataMergerService;

    @GetMapping
    public String showDataMergerPage() {
        return "utils/data-merger";
    }

    @PostMapping("/process")
    public String processFiles(
            @RequestParam("sourceFile") MultipartFile sourceFile,
            @RequestParam("collectedFile") MultipartFile collectedFile,
            Model model) {

        log.info("Получена форма с файлами: {} и {}",
                sourceFile.getOriginalFilename(), collectedFile.getOriginalFilename());

        try {
            // Базовая валидация файлов
            if (sourceFile.isEmpty() || collectedFile.isEmpty()) {
                throw new IllegalArgumentException("Оба файла должны быть загружены");
            }

            List<MergedDataRow> result = dataMergerService.mergeData(sourceFile, collectedFile);

            model.addAttribute("success", true);
            model.addAttribute("resultCount", result.size());
            model.addAttribute("results", result);

            log.info("Обработка завершена успешно. Результат: {} строк", result.size());

        } catch (IllegalArgumentException e) {
            log.warn("Ошибка валидации: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при обработке файлов", e);
            model.addAttribute("error", "Ошибка при обработке файлов: " + e.getMessage());
        }

        return "utils/data-merger";
    }

    @PostMapping("/download")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam("sourceFile") MultipartFile sourceFile,
            @RequestParam("collectedFile") MultipartFile collectedFile,
            @RequestParam(value = "sourceIdColumn", required = false) String sourceIdColumn,
            @RequestParam(value = "sourceClientModelColumn", required = false) String sourceClientModelColumn,
            @RequestParam(value = "sourceAnalogModelColumn", required = false) String sourceAnalogModelColumn,
            @RequestParam(value = "sourceCoefficientColumn", required = false) String sourceCoefficientColumn,
            @RequestParam(value = "collectedAnalogModelColumn", required = false) String collectedAnalogModelColumn,
            @RequestParam(value = "collectedLinkColumn", required = false) String collectedLinkColumn) {

        log.info("Запрос на скачивание файла с данными: {} и {}",
                sourceFile.getOriginalFilename(), collectedFile.getOriginalFilename());

        try {
            // Базовая валидация файлов
            if (sourceFile.isEmpty() || collectedFile.isEmpty()) {
                throw new IllegalArgumentException("Оба файла должны быть загружены");
            }

            // Создаем маппинг колонок
            Map<String, Integer> columnMapping = createColumnMapping(
                    sourceIdColumn, sourceClientModelColumn, sourceAnalogModelColumn,
                    sourceCoefficientColumn, collectedAnalogModelColumn, collectedLinkColumn);

            // Генерируем файл с учетом маппинга колонок
            Path resultFile = dataMergerService.mergeDataAndGenerateFile(sourceFile, collectedFile, columnMapping);

            // Создаем ресурс для скачивания
            Resource resource = new UrlResource(resultFile.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("Не удалось прочитать файл результата");
            }

            // Определяем имя файла для скачивания
            String downloadFileName = "merged-data.csv";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                           "attachment; filename=\"" + downloadFileName + "\"")
                    .body(resource);

        } catch (IllegalArgumentException e) {
            log.warn("Ошибка валидации при скачивании: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Ошибка при генерации файла для скачивания", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/analyze-headers")
    @ResponseBody
    public Map<String, Object> analyzeHeaders(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileType") String fileType) {

        Map<String, Object> response = new HashMap<>();

        try {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("Файл не может быть пустым");
            }

            List<String> headers = dataMergerService.analyzeFileHeaders(file);

            response.put("success", true);
            response.put("headers", headers);
            response.put("fileType", fileType);

            log.info("Анализ заголовков файла {} завершен. Найдено {} колонок",
                    file.getOriginalFilename(), headers.size());

        } catch (Exception e) {
            log.error("Ошибка при анализе заголовков файла", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    private Map<String, Integer> createColumnMapping(String sourceIdColumn, String sourceClientModelColumn,
                                                   String sourceAnalogModelColumn, String sourceCoefficientColumn,
                                                   String collectedAnalogModelColumn, String collectedLinkColumn) {
        Map<String, Integer> mapping = new HashMap<>();

        if (sourceIdColumn != null && !sourceIdColumn.isEmpty()) {
            mapping.put("sourceIdColumn", Integer.parseInt(sourceIdColumn));
        }
        if (sourceClientModelColumn != null && !sourceClientModelColumn.isEmpty()) {
            mapping.put("sourceClientModelColumn", Integer.parseInt(sourceClientModelColumn));
        }
        if (sourceAnalogModelColumn != null && !sourceAnalogModelColumn.isEmpty()) {
            mapping.put("sourceAnalogModelColumn", Integer.parseInt(sourceAnalogModelColumn));
        }
        if (sourceCoefficientColumn != null && !sourceCoefficientColumn.isEmpty()) {
            mapping.put("sourceCoefficientColumn", Integer.parseInt(sourceCoefficientColumn));
        }
        if (collectedAnalogModelColumn != null && !collectedAnalogModelColumn.isEmpty()) {
            mapping.put("collectedAnalogModelColumn", Integer.parseInt(collectedAnalogModelColumn));
        }
        if (collectedLinkColumn != null && !collectedLinkColumn.isEmpty()) {
            mapping.put("collectedLinkColumn", Integer.parseInt(collectedLinkColumn));
        }

        return mapping;
    }
}