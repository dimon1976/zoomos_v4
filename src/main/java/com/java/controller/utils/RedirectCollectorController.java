package com.java.controller.utils;

import com.java.dto.utils.RedirectCollectorDto;
import com.java.model.entity.FileMetadata;
import com.java.service.file.FileAnalyzerService;
import com.java.service.utils.RedirectCollectorService;
import com.java.service.utils.AsyncRedirectCollectorService;
import com.java.util.ControllerUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Контроллер для утилиты сбора финальных URL после редиректов
 */
@Controller
@RequestMapping("/utils/redirect-collector")
@RequiredArgsConstructor
@Slf4j
public class RedirectCollectorController {

    private final FileAnalyzerService fileAnalyzerService;
    private final ControllerUtils controllerUtils;
    private final RedirectCollectorService redirectCollectorService;
    private final AsyncRedirectCollectorService asyncRedirectCollectorService;

    /**
     * Главная страница утилиты
     */
    @GetMapping
    public String index(Model model) {
        model.addAttribute("pageTitle", "Сбор финальных ссылок");
        return "utils/redirect-collector";
    }

    /**
     * Загрузка и анализ файла
     */
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file,
                           RedirectAttributes redirectAttributes,
                           HttpSession session) {
        log.debug("POST request to /utils/redirect-collector/upload with file: {}", 
                 file != null ? file.getOriginalFilename() : "null");

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Выберите файл для загрузки");
            return "redirect:/utils/redirect-collector";
        }

        try {
            // Анализ файла
            FileMetadata metadata = fileAnalyzerService.analyzeFile(file, "redirect-collector");

            // Сохраняем метаданные в сессии
            session.setAttribute("redirectCollectorMetadata", metadata);

            log.debug("File analyzed successfully: {} columns", 
                     metadata.getTotalColumns());

            return "redirect:/utils/redirect-collector/configure";

        } catch (Exception e) {
            log.error("Error uploading file for redirect collector", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Ошибка загрузки файла: " + e.getMessage());
            return "redirect:/utils/redirect-collector";
        }
    }

    /**
     * Страница настройки колонок
     */
    @GetMapping("/configure")
    public String configure(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        log.debug("GET request to /utils/redirect-collector/configure");

        FileMetadata metadata = (FileMetadata) session.getAttribute("redirectCollectorMetadata");
        if (metadata == null) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Сессия истекла. Пожалуйста, загрузите файл заново.");
            return "redirect:/utils/redirect-collector";
        }

        model.addAttribute("pageTitle", "Настройка сбора редиректов");
        model.addAttribute("metadata", metadata);
        model.addAttribute("redirectCollectorDto", RedirectCollectorDto.builder()
            .maxRedirects(5)
            .timeoutSeconds(10)
            .outputFormat("CSV")
            .csvDelimiter(";")
            .csvEncoding("UTF-8")
            .build());

        return "utils/redirect-collector-configure";
    }

    /**
     * Обработка файла и скачивание результата
     */
    @PostMapping("/process")
    public ResponseEntity<Resource> processRedirects(@Valid @ModelAttribute RedirectCollectorDto dto,
                                                   BindingResult bindingResult,
                                                   HttpSession session,
                                                   RedirectAttributes redirectAttributes) {
        log.debug("POST request to /utils/redirect-collector/process with dto: {}", dto);

        if (bindingResult.hasErrors()) {
            log.debug("Validation errors in redirect collector DTO: {}", bindingResult.getAllErrors());
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Ошибки валидации: " + bindingResult.getFieldError().getDefaultMessage());
            return ResponseEntity.badRequest().build();
        }

        FileMetadata metadata = (FileMetadata) session.getAttribute("redirectCollectorMetadata");
        if (metadata == null) {
            log.debug("No metadata found in session for redirect collector");
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Сессия истекла. Пожалуйста, загрузите файл заново.");
            return ResponseEntity.badRequest().build();
        }

        try {
            // Обрабатываем сбор редиректов
            byte[] resultBytes = redirectCollectorService.processRedirectCollection(metadata, dto);

            // Определяем имя файла и content-type
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName;
            String contentType;

            if ("excel".equalsIgnoreCase(dto.getOutputFormat())) {
                fileName = "redirect_collector_result_" + timestamp + ".xlsx";
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else {
                fileName = "redirect_collector_result_" + timestamp + ".csv";
                contentType = "text/csv; charset=utf-8";
            }

            // Возвращаем файл для скачивания
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(new ByteArrayResource(resultBytes));

        } catch (Exception e) {
            log.error("Error processing redirect collection", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Ошибка обработки: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        } finally {
            // Очищаем сессию
            session.removeAttribute("redirectCollectorMetadata");
        }
    }

    /**
     * Асинхронная обработка файла с WebSocket уведомлениями
     */
    @PostMapping("/process-async")
    @ResponseBody
    public ResponseEntity<?> processRedirectsAsync(@Valid @RequestBody RedirectCollectorDto dto,
                                                 BindingResult bindingResult) {
        log.debug("POST request to /utils/redirect-collector/process-async with dto: {}", dto);

        if (bindingResult.hasErrors()) {
            log.debug("Validation errors in redirect collector DTO: {}", bindingResult.getAllErrors());
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Ошибки валидации: " + bindingResult.getFieldError().getDefaultMessage()));
        }

        if (dto.getTempFilePath() == null) {
            log.debug("No temp file path provided for async redirect collector");
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Временный файл не найден. Пожалуйста, загрузите файл заново."));
        }

        try {
            // Генерируем уникальный ID операции
            String operationId = "redirect-" + System.currentTimeMillis() + "-" + 
                                Math.round(Math.random() * 1000);
            
            log.info("Запуск асинхронного сбора редиректов с ID операции: {}", operationId);
            
            // Запускаем асинхронную обработку
            asyncRedirectCollectorService.startAsyncRedirectCollection(operationId, dto);

            // Возвращаем ID операции для отслеживания прогресса
            return ResponseEntity.ok()
                .body(Map.of(
                    "operationId", operationId,
                    "message", "Сбор редиректов начат. Следите за прогрессом через WebSocket.",
                    "websocketTopic", "/topic/redirect-progress/" + operationId
                ));

        } catch (Exception e) {
            log.error("Error starting async redirect collection", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Ошибка запуска: " + e.getMessage()));
        }
    }
}