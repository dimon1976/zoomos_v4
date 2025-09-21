package com.java.controller.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контроллер для утилиты объединения данных товаров с аналогами и ссылками
 */
@Controller
@RequestMapping("/utils/data-merger")
@RequiredArgsConstructor
@Slf4j
public class DataMergerController {

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
     * Обработка загруженных файлов
     * Пока просто проверяем что файлы получены
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
}