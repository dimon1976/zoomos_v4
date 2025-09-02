package com.java.controller.utils;

import com.java.dto.FileAnalysisResultDto;
import com.java.dto.utils.UrlCleanerDto;
import com.java.mapper.FileMetadataMapper;
import com.java.model.entity.FileMetadata;
import com.java.service.file.FileAnalyzerService;
import com.java.service.utils.UrlCleanerService;
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
 * Контроллер для утилиты очистки URL
 */
@Controller
@RequestMapping("/utils/url-cleaner")
@RequiredArgsConstructor
@Slf4j
public class UrlCleanerController {

    private final FileAnalyzerService fileAnalyzerService;
    private final ControllerUtils controllerUtils;
    private final UrlCleanerService urlCleanerService;

    /**
     * Главная страница утилиты
     */
    @GetMapping
    public String index(Model model) {
        model.addAttribute("pageTitle", "Очистка URL");
        return "utils/url-cleaner";
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
            return "redirect:/utils/url-cleaner";
        }

        try {
            // Анализ файла
            FileMetadata metadata = fileAnalyzerService.analyzeFile(file, "url-cleaner");
            
            // Сохранение в сессии
            session.setAttribute("urlCleanerFile", metadata);
            
            redirectAttributes.addFlashAttribute("success", 
                "Файл успешно проанализирован. Выберите колонки для обработки.");
            
            return "redirect:/utils/url-cleaner/configure";
            
        } catch (Exception e) {
            log.error("Failed to analyze file: {}", file.getOriginalFilename(), e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при анализе файла: " + e.getMessage());
            return "redirect:/utils/url-cleaner";
        }
    }

    /**
     * Страница настройки колонок
     */
    @GetMapping("/configure")
    public String configure(Model model, HttpSession session) {
        FileMetadata metadata = (FileMetadata) session.getAttribute("urlCleanerFile");
        
        if (metadata == null) {
            return "redirect:/utils/url-cleaner";
        }

        FileAnalysisResultDto analysisResult = FileMetadataMapper.toAnalysisDto(metadata);
        
        model.addAttribute("pageTitle", "Настройка колонок");
        model.addAttribute("fileMetadata", analysisResult);
        model.addAttribute("urlCleanerDto", new UrlCleanerDto());
        
        return "utils/url-cleaner-configure";
    }

    /**
     * Обработка файла
     */
    @PostMapping("/process")
    public ResponseEntity<Resource> processFile(@Valid @ModelAttribute UrlCleanerDto dto,
                                               BindingResult bindingResult,
                                               HttpSession session) {
        
        log.debug("Received DTO: removeUtmParams={}, removeReferralParams={}, removeTrackingParams={}, preserveYandexSku={}", 
                 dto.isRemoveUtmParams(), dto.isRemoveReferralParams(), dto.isRemoveTrackingParams(), dto.isPreserveYandexSku());
        
        if (bindingResult.hasErrors()) {
            throw new IllegalArgumentException("Ошибка валидации параметров");
        }

        FileMetadata metadata = (FileMetadata) session.getAttribute("urlCleanerFile");
        if (metadata == null) {
            throw new IllegalArgumentException("Файл не найден в сессии");
        }

        try {
            // Обработка через сервис
            byte[] resultData = urlCleanerService.processUrlCleaning(metadata, dto);
            
            // Очистка сессии
            session.removeAttribute("urlCleanerFile");
            
            // Формирование имени файла и типа контента
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName;
            MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
            
            if ("csv".equalsIgnoreCase(dto.getOutputFormat())) {
                fileName = "url-cleaner-result_" + timestamp + ".csv";
            } else {
                fileName = "url-cleaner-result_" + timestamp + ".xlsx";
            }
            
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(new ByteArrayResource(resultData));
                    
        } catch (Exception e) {
            log.error("Failed to process URL cleaning for file: {}", metadata.getOriginalFilename(), e);
            throw new RuntimeException("Ошибка при обработке файла: " + e.getMessage(), e);
        }
    }

    /**
     * Отмена операции
     */
    @PostMapping("/cancel")
    public String cancel(HttpSession session) {
        session.removeAttribute("urlCleanerFile");
        return "redirect:/utils/url-cleaner";
    }
}