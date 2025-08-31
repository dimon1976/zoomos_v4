package com.java.controller.utils;

import com.java.dto.FileAnalysisResultDto;
import com.java.dto.utils.BarcodeMatchDto;
import com.java.mapper.FileMetadataMapper;
import com.java.model.entity.FileMetadata;
import com.java.service.file.FileAnalyzerService;
import com.java.service.utils.BarcodeMatchService;
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
 * Контроллер для утилиты сопоставления штрихкодов
 */
@Controller
@RequestMapping("/utils/barcode-match")
@RequiredArgsConstructor
@Slf4j
public class BarcodeMatchController {

    private final FileAnalyzerService fileAnalyzerService;
    private final ControllerUtils controllerUtils;
    private final BarcodeMatchService barcodeMatchService;

    /**
     * Главная страница утилиты
     */
    @GetMapping
    public String index(Model model) {
        model.addAttribute("pageTitle", "Сопоставление штрихкодов");
        return "utils/barcode-match";
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
            return "redirect:/utils/barcode-match";
        }

        try {
            // Анализ файла
            FileMetadata metadata = fileAnalyzerService.analyzeFile(file, "barcode-match");
            
            // Сохранение в сессии
            session.setAttribute("barcodeMatchFile", metadata);
            
            redirectAttributes.addFlashAttribute("success", 
                "Файл успешно проанализирован. Выберите колонки для обработки.");
            
            return "redirect:/utils/barcode-match/configure";
            
        } catch (Exception e) {
            log.error("Failed to analyze file: {}", file.getOriginalFilename(), e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при анализе файла: " + e.getMessage());
            return "redirect:/utils/barcode-match";
        }
    }

    /**
     * Страница настройки колонок
     */
    @GetMapping("/configure")
    public String configure(Model model, HttpSession session) {
        FileMetadata metadata = (FileMetadata) session.getAttribute("barcodeMatchFile");
        
        if (metadata == null) {
            return "redirect:/utils/barcode-match";
        }

        FileAnalysisResultDto analysisResult = FileMetadataMapper.toAnalysisDto(metadata);
        
        model.addAttribute("pageTitle", "Настройка колонок");
        model.addAttribute("fileMetadata", analysisResult);
        model.addAttribute("barcodeMatchDto", new BarcodeMatchDto());
        
        return "utils/barcode-match-configure";
    }

    /**
     * Обработка файла
     */
    @PostMapping("/process")
    public ResponseEntity<Resource> processFile(@Valid @ModelAttribute BarcodeMatchDto dto,
                                               BindingResult bindingResult,
                                               HttpSession session) {
        
        if (bindingResult.hasErrors()) {
            throw new IllegalArgumentException("Ошибка валидации параметров");
        }

        FileMetadata metadata = (FileMetadata) session.getAttribute("barcodeMatchFile");
        if (metadata == null) {
            throw new IllegalArgumentException("Файл не найден в сессии");
        }

        try {
            // Обработка через сервис
            byte[] resultData = barcodeMatchService.processBarcodeMatching(metadata, dto);
            
            // Очистка сессии
            session.removeAttribute("barcodeMatchFile");
            
            // Формирование имени файла и типа контента
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName;
            MediaType contentType;
            
            if ("csv".equalsIgnoreCase(dto.getOutputFormat())) {
                fileName = "barcode-match-result_" + timestamp + ".csv";
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            } else {
                fileName = "barcode-match-result_" + timestamp + ".xlsx";
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            }
            
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(new ByteArrayResource(resultData));
                    
        } catch (Exception e) {
            log.error("Failed to process barcode matching for file: {}", metadata.getOriginalFilename(), e);
            throw new RuntimeException("Ошибка при обработке файла: " + e.getMessage(), e);
        }
    }

    /**
     * Отмена операции
     */
    @PostMapping("/cancel")
    public String cancel(HttpSession session) {
        session.removeAttribute("barcodeMatchFile");
        return "redirect:/utils/barcode-match";
    }
}