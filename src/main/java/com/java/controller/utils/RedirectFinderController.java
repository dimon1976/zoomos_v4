package com.java.controller.utils;

import com.java.dto.FileAnalysisResultDto;
import com.java.dto.utils.RedirectFinderDto;
import com.java.mapper.FileMetadataMapper;
import com.java.model.entity.FileMetadata;
import com.java.service.file.FileAnalyzerService;
import com.java.service.utils.RedirectFinderService;
import com.java.service.utils.redirect.AsyncRedirectService;
import com.java.model.utils.RedirectProcessingRequest;
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

/**
 * Контроллер для утилиты поиска финальных ссылок после HTTP редиректов
 */
@Controller
@RequestMapping("/utils/redirect-finder")
@RequiredArgsConstructor
@Slf4j
public class RedirectFinderController {

    private final FileAnalyzerService fileAnalyzerService;
    private final ControllerUtils controllerUtils;
    private final RedirectFinderService redirectFinderService;
    private final AsyncRedirectService asyncRedirectService;

    /**
     * Главная страница утилиты
     */
    @GetMapping
    public String index(Model model) {
        model.addAttribute("pageTitle", "Поиск финальных ссылок");
        model.addAttribute("description", 
            "Утилита для получения финальных URL после обработки HTTP редиректов с защитой от антиботных систем");
        
        return "utils/redirect-finder";
    }

    /**
     * Загрузка и анализ файла
     */
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file, 
                           HttpSession session, 
                           RedirectAttributes redirectAttributes) {
        
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Выберите файл для загрузки");
            return "redirect:/utils/redirect-finder";
        }

        try {
            log.info("Загружаем файл для анализа редиректов: {}", file.getOriginalFilename());
            
            // Анализ файла
            FileMetadata metadata = fileAnalyzerService.analyzeFile(file, "redirect-finder");
            
            // Сохранение в сессии
            session.setAttribute("redirectFinderFile", metadata);
            
            redirectAttributes.addFlashAttribute("success", 
                "Файл успешно проанализирован. Выберите колонку с URL.");
            
            return "redirect:/utils/redirect-finder/configure";
            
        } catch (Exception e) {
            log.error("Failed to analyze file: {}", file.getOriginalFilename(), e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при анализе файла: " + e.getMessage());
            return "redirect:/utils/redirect-finder";
        }
    }

    /**
     * Страница настройки колонок
     */
    @GetMapping("/configure")
    public String configure(Model model, HttpSession session) {
        FileMetadata metadata = (FileMetadata) session.getAttribute("redirectFinderFile");
        
        if (metadata == null) {
            return "redirect:/utils/redirect-finder";
        }

        FileAnalysisResultDto analysisResult = FileMetadataMapper.toAnalysisDto(metadata);
        
        model.addAttribute("pageTitle", "Настройка обработки редиректов");
        model.addAttribute("fileMetadata", analysisResult);
        model.addAttribute("redirectFinderDto", new RedirectFinderDto());
        
        return "utils/redirect-finder-configure";
    }

    /**
     * Асинхронная обработка файла (рекомендуемый способ)
     */
    @PostMapping("/process-async")
    @ResponseBody
    public ResponseEntity<String> processFileAsync(@Valid @ModelAttribute RedirectFinderDto dto,
                                                  BindingResult bindingResult,
                                                  HttpSession session) {
        
        log.info("Запускаем асинхронную обработку редиректов");
        log.debug("Async DTO: urlColumn={}, maxRedirects={}, timeout={}, delayMs={}", 
                 dto.getUrlColumn(), dto.getMaxRedirects(), dto.getTimeoutMs(), dto.getDelayMs());
        
        if (bindingResult.hasErrors()) {
            log.error("Validation errors: {}", bindingResult.getAllErrors());
            return ResponseEntity.badRequest()
                    .body("Ошибка валидации параметров: " + 
                          bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        FileMetadata metadata = (FileMetadata) session.getAttribute("redirectFinderFile");
        if (metadata == null) {
            return ResponseEntity.badRequest().body("Файл не найден в сессии");
        }

        try {
            log.info("Подготовка данных для асинхронной обработки: {}", metadata.getOriginalFilename());
            
            // Подготовка запроса
            RedirectProcessingRequest request = redirectFinderService.prepareAsyncRequest(metadata, dto);
            
            // Очистка сессии (файл уже обработан)
            session.removeAttribute("redirectFinderFile");
            
            // Запуск асинхронной обработки
            asyncRedirectService.processRedirectsAsync(request);
            
            log.info("Асинхронная обработка запущена для файла: {}", metadata.getOriginalFilename());
            
            return ResponseEntity.ok("Обработка запущена! Вы можете перейти к другим задачам. Уведомление о завершении придёт автоматически.");
                    
        } catch (Exception e) {
            log.error("Failed to start async redirect processing for file: {}", metadata.getOriginalFilename(), e);
            return ResponseEntity.status(500)
                    .body("Ошибка при запуске обработки: " + e.getMessage());
        }
    }
    
    /**
     * Обработка файла (синхронная - для малых файлов)
     */
    @PostMapping("/process")
    public ResponseEntity<Resource> processFile(@Valid @ModelAttribute RedirectFinderDto dto,
                                               BindingResult bindingResult,
                                               HttpSession session) {
        
        log.debug("Received DTO: urlColumn={}, maxRedirects={}, timeout={}, usePlaywright={}", 
                 dto.getUrlColumn(), dto.getMaxRedirects(), dto.getTimeoutMs(), dto.getUsePlaywright());
        
        if (bindingResult.hasErrors()) {
            log.error("Validation errors: {}", bindingResult.getAllErrors());
            throw new IllegalArgumentException("Ошибка валидации параметров: " + 
                bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        FileMetadata metadata = (FileMetadata) session.getAttribute("redirectFinderFile");
        if (metadata == null) {
            throw new IllegalArgumentException("Файл не найден в сессии");
        }

        try {
            log.info("Начинаем обработку редиректов для файла: {}", metadata.getOriginalFilename());
            
            // Обработка через сервис
            byte[] resultData = redirectFinderService.processRedirectFinding(metadata, dto);
            
            // Очистка сессии
            session.removeAttribute("redirectFinderFile");
            
            // Формирование имени файла и типа контента
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName;
            MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
            
            if ("csv".equalsIgnoreCase(dto.getOutputFormat())) {
                fileName = "redirect-finder-result_" + timestamp + ".csv";
            } else {
                fileName = "redirect-finder-result_" + timestamp + ".xlsx";
            }
            
            log.info("Обработка редиректов завершена. Отдаем файл: {}", fileName);
            
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(new ByteArrayResource(resultData));
                    
        } catch (Exception e) {
            log.error("Failed to process redirect finding for file: {}", metadata.getOriginalFilename(), e);
            throw new RuntimeException("Ошибка при обработке файла: " + e.getMessage(), e);
        }
    }

    /**
     * Отмена операции
     */
    @PostMapping("/cancel")
    public String cancel(HttpSession session) {
        session.removeAttribute("redirectFinderFile");
        log.debug("Операция обработки редиректов отменена");
        return "redirect:/utils/redirect-finder";
    }
}