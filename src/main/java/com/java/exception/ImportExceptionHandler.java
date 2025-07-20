package com.java.exception;

import com.java.service.imports.validation.TemplateValidationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


/**
 * Глобальный обработчик исключений для системы импорта
 */
@ControllerAdvice
@Slf4j
public class ImportExceptionHandler {

    /**
     * Обработка общих исключений импорта
     */
    @ExceptionHandler(ImportException.class)
    public String handleImportException(ImportException ex, Model model,
                                        HttpServletRequest request) {
        log.error("Ошибка импорта: {}", ex.getMessage(), ex);

        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("errorCode", ex.getCode());
        model.addAttribute("requestUri", request.getRequestURI());

        return "error/import-error";
    }

    /**
     * Обработка ошибок обработки файла
     */
    @ExceptionHandler(FileProcessingException.class)
    public String handleFileProcessingException(FileProcessingException ex,
                                                Model model,
                                                HttpServletRequest request) {
        log.error("Ошибка обработки файла {}: {}", ex.getFileName(), ex.getMessage(), ex);

        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("fileName", ex.getFileName());
        model.addAttribute("rowNumber", ex.getRowNumber());
        model.addAttribute("requestUri", request.getRequestURI());

        return "error/file-error";
    }

    /**
     * Обработка ошибок валидации шаблона
     */
    @ExceptionHandler(TemplateValidationService.ValidationException.class)
    public String handleTemplateValidationException(TemplateValidationService.ValidationException ex,
                                                    RedirectAttributes redirectAttributes,
                                                    HttpServletRequest request) {
        log.error("Ошибка валидации шаблона: {}", ex.getMessage());

        redirectAttributes.addFlashAttribute("errorMessage", "Ошибка валидации шаблона");
        redirectAttributes.addFlashAttribute("validationErrors", ex.getErrors());

        // Возвращаемся на предыдущую страницу
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/import/templates");
    }

    /**
     * Обработка превышения размера файла
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public String handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex,
                                              Model model) {
        log.error("Превышен максимальный размер файла: {}", ex.getMessage());

        model.addAttribute("errorMessage",
                "Размер файла превышает максимально допустимый (600 МБ)");

        return "error/file-error";
    }

    /**
     * Обработка превышения квоты
     */
    @ExceptionHandler(ImportQuotaExceededException.class)
    public String handleQuotaExceeded(ImportQuotaExceededException ex,
                                      Model model) {
        log.error("Превышена квота импорта: {}", ex.getMessage());

        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("limit", ex.getLimit());
        model.addAttribute("current", ex.getCurrent());

        return "error/quota-exceeded";
    }

    /**
     * Обработка неподдерживаемых операций
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public String handleUnsupportedOperation(UnsupportedOperationException ex,
                                             Model model,
                                             HttpServletRequest request) {
        log.error("Неподдерживаемая операция: {}", ex.getMessage());

        model.addAttribute("errorMessage",
                "Операция не поддерживается: " + ex.getMessage());
        model.addAttribute("requestUri", request.getRequestURI());

        return "error/general";
    }
}