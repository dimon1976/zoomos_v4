package com.java.controller.utils;

import com.java.dto.FileAnalysisResultDto;
import com.java.dto.utils.StatsProcessDto;
import com.java.mapper.FileMetadataMapper;
import com.java.model.entity.FileMetadata;
import com.java.service.file.FileAnalyzerService;
import com.java.service.utils.StatsProcessorService;
import com.java.service.utils.AsyncStatsProcessorService;
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
import java.util.UUID;

/**
 * Контроллер для утилиты обработки файлов статистики
 * Аналог старой утилиты "Обработка файла статистики" с чекбоксами
 */
@Controller
@RequestMapping("/utils/stats-processor")
@RequiredArgsConstructor
@Slf4j
public class StatsProcessorController {

    private final FileAnalyzerService fileAnalyzerService;
    private final ControllerUtils controllerUtils;
    private final StatsProcessorService statsProcessorService;
    private final AsyncStatsProcessorService asyncStatsProcessorService;

    /**
     * Главная страница утилиты
     */
    @GetMapping
    public String index(Model model) {
        model.addAttribute("pageTitle", "Обработка файла статистики");
        return "utils/stats-processor";
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
            return "redirect:/utils/stats-processor";
        }

        try {
            // Анализ файла
            FileMetadata metadata = fileAnalyzerService.analyzeFile(file, "stats-processor");
            
            // Сохранение в сессии
            session.setAttribute("statsProcessorFile", metadata);
            
            redirectAttributes.addFlashAttribute("success", 
                "Файл успешно проанализирован. Выберите параметры обработки.");
            
            return "redirect:/utils/stats-processor/configure";
            
        } catch (Exception e) {
            log.error("Failed to analyze file: {}", file.getOriginalFilename(), e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при анализе файла: " + e.getMessage());
            return "redirect:/utils/stats-processor";
        }
    }

    /**
     * Страница настройки параметров обработки
     */
    @GetMapping("/configure")
    public String configure(Model model, HttpSession session) {
        FileMetadata metadata = (FileMetadata) session.getAttribute("statsProcessorFile");
        
        if (metadata == null) {
            return "redirect:/utils/stats-processor";
        }

        FileAnalysisResultDto analysisResult = FileMetadataMapper.toAnalysisDto(metadata);
        
        model.addAttribute("pageTitle", "Параметры обработки");
        model.addAttribute("fileMetadata", analysisResult);
        model.addAttribute("statsProcessDto", new StatsProcessDto());
        
        return "utils/stats-processor-configure";
    }

    /**
     * Асинхронная обработка файла (не блокирует пользователя)
     */
    @PostMapping("/process-async")
    @ResponseBody
    public ResponseEntity<String> processFileAsync(@Valid @ModelAttribute StatsProcessDto dto,
                                                  BindingResult bindingResult,
                                                  HttpSession session) {
        
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body("Ошибка валидации параметров");
        }

        FileMetadata metadata = (FileMetadata) session.getAttribute("statsProcessorFile");
        if (metadata == null) {
            return ResponseEntity.badRequest().body("Файл не найден в сессии");
        }

        try {
            log.info("Запуск асинхронной обработки файла статистики: {}", metadata.getOriginalFilename());
            
            // Генерируем уникальный ID для операции
            String operationId = UUID.randomUUID().toString();
            
            // Очистка сессии (файл уже обработан)
            session.removeAttribute("statsProcessorFile");
            
            // Запуск асинхронной обработки
            asyncStatsProcessorService.processStatsAsync(metadata, dto, operationId);
            
            log.info("Асинхронная обработка запущена для файла: {}", metadata.getOriginalFilename());
            
            return ResponseEntity.ok("Обработка запущена! Вы можете перейти к другим задачам. Уведомление о завершении придёт автоматически.");
                    
        } catch (Exception e) {
            log.error("Failed to start async stats processing for file: {}", metadata.getOriginalFilename(), e);
            return ResponseEntity.status(500)
                    .body("Ошибка при запуске обработки: " + e.getMessage());
        }
    }

    /**
     * Обработка файла с выбранными параметрами (синхронная для малых файлов)
     */
    @PostMapping("/process")
    public ResponseEntity<Resource> processFile(@Valid @ModelAttribute StatsProcessDto dto,
                                               BindingResult bindingResult,
                                               HttpSession session) {
        
        if (bindingResult.hasErrors()) {
            throw new IllegalArgumentException("Ошибка валидации параметров");
        }

        FileMetadata metadata = (FileMetadata) session.getAttribute("statsProcessorFile");
        if (metadata == null) {
            throw new IllegalArgumentException("Файл не найден в сессии");
        }

        try {
            // Обработка через сервис
            byte[] resultData = statsProcessorService.processStatsFile(metadata, dto);
            
            // Очистка сессии
            session.removeAttribute("statsProcessorFile");
            
            // Формирование имени файла и типа контента
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName;
            MediaType contentType;
            
            if ("csv".equalsIgnoreCase(dto.getOutputFormat())) {
                fileName = "stats-processed_" + timestamp + ".csv";
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            } else {
                fileName = "stats-processed_" + timestamp + ".xlsx";
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            }
            
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(new ByteArrayResource(resultData));
                    
        } catch (Exception e) {
            log.error("Failed to process stats file: {}", metadata.getOriginalFilename(), e);
            throw new RuntimeException("Ошибка при обработке файла: " + e.getMessage(), e);
        }
    }

    /**
     * Отмена операции
     */
    @PostMapping("/cancel")
    public String cancel(HttpSession session) {
        session.removeAttribute("statsProcessorFile");
        return "redirect:/utils/stats-processor";
    }
}