package com.java.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.Optional;

/**
 * Глобальный обработчик исключений для всего приложения.
 * Обеспечивает централизованную обработку различных типов исключений с соответствующими
 * HTTP-статусами и страницами ошибок.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Константы для представлений ошибок
    private static final String DEFAULT_ERROR_VIEW = "error/general";
    private static final String FILE_ERROR_VIEW = "error/file-error";
    private static final String ACCESS_DENIED_VIEW = "error/access-denied";
    private static final String NOT_FOUND_VIEW = "error/not-found";

    // Константы для атрибутов ошибок
    private static final String ERROR_MESSAGE_ATTR = "errorMessage";
    private static final String REQUEST_URI_ATTR = "requestUri";
    private static final String TIMESTAMP_ATTR = "timestamp";

    /**
     * Обработка EntityNotFoundException (объект не найден)
     *
     * @param ex исключение
     * @param model модель представления
     * @param request HTTP-запрос
     * @return имя представления ошибки
     */
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleEntityNotFoundException(
            EntityNotFoundException ex, Model model, HttpServletRequest request) {

        logError("Сущность не найдена", ex);
        setErrorAttributes(model, ex.getMessage(), request);

        return NOT_FOUND_VIEW;
    }

    /**
     * Обработка ошибок доступа к файлу
     *
     * @param ex исключение
     * @param model модель представления
     * @param request HTTP-запрос
     * @return имя представления ошибки
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDeniedException(
            AccessDeniedException ex, Model model, HttpServletRequest request) {

        logError("Доступ запрещен", ex);
        setErrorAttributes(model, "Доступ запрещен: " + ex.getMessage(), request);

        return ACCESS_DENIED_VIEW;
    }

    /**
     * Обработка ошибок валидации форм
     *
     * @param ex исключение
     * @param model модель представления
     * @param request HTTP-запрос
     * @return имя представления ошибки
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleValidationExceptions(
            Exception ex, Model model, HttpServletRequest request) {

        logError("Ошибка валидации", ex);
        setErrorAttributes(model,
                "Ошибка валидации данных. Пожалуйста, проверьте введенные данные.", request);

        return DEFAULT_ERROR_VIEW;
    }

    /**
     * Обработка ошибок валидации полей
     *
     * @param ex исключение
     * @param model модель представления
     * @param request HTTP-запрос
     * @return имя представления ошибки
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleConstraintViolationException(
            ConstraintViolationException ex, Model model, HttpServletRequest request) {

        logError("Нарушение ограничений", ex);
        setErrorAttributes(model, "Ошибка валидации данных: " + ex.getMessage(), request);

        return DEFAULT_ERROR_VIEW;
    }

    /**
     * Обработка ошибок размера загружаемого файла
     *
     * @param ex исключение
     * @param model модель представления
     * @param request HTTP-запрос
     * @return имя представления ошибки
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, Model model, HttpServletRequest request) {

        logError("Превышен размер файла", ex);
        setErrorAttributes(model,
                "Превышен максимальный размер файла. Максимальный размер: 1000MB.", request);

        return FILE_ERROR_VIEW;
    }

    /**
     * Обработка ошибок файловых операций
     *
     * @param ex исключение
     * @param model модель представления
     * @param request HTTP-запрос
     * @return имя представления ошибки
     */
    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleFileProcessingError(
            IOException ex, Model model, HttpServletRequest request) {

        logError("Ошибка обработки файла", ex);
        setErrorAttributes(model, "Ошибка обработки файла: " + ex.getMessage(), request);

        return FILE_ERROR_VIEW;
    }

    /**
     * Обработка пользовательских исключений файловых операций
     *
     * @param ex исключение
     * @param model модель представления
     * @param request HTTP-запрос
     * @param redirectAttributes атрибуты перенаправления
     * @return имя представления ошибки или объект перенаправления
     */
    @ExceptionHandler(FileOperationException.class)
    public Object handleFileOperationException(
            FileOperationException ex, Model model,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {

        logError("Ошибка файловой операции", ex);

        // Если указано перенаправление, используем его
        if (hasRedirectUrl(ex)) {
            return redirectWithError(ex, redirectAttributes);
        }

        // Иначе отображаем страницу ошибки
        setErrorAttributes(model, ex.getMessage(), request);
        return FILE_ERROR_VIEW;
    }

    /**
     * Проверяет, есть ли URL для перенаправления в исключении.
     *
     * @param ex исключение
     * @return true, если есть URL для перенаправления
     */
    private boolean hasRedirectUrl(FileOperationException ex) {
        return ex.getRedirectUrl() != null && !ex.getRedirectUrl().isEmpty();
    }

    /**
     * Создает перенаправление с сообщением об ошибке.
     *
     * @param ex исключение
     * @param redirectAttributes атрибуты перенаправления
     * @return объект перенаправления
     */
    private RedirectView redirectWithError(
            FileOperationException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(ERROR_MESSAGE_ATTR, ex.getMessage());
        return new RedirectView(ex.getRedirectUrl(), true);
    }

    /**
     * Обработка всех остальных исключений
     *
     * @param ex исключение
     * @param model модель представления
     * @param request HTTP-запрос
     * @return имя представления ошибки
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(
            Exception ex, Model model, HttpServletRequest request) {

        // Извлекаем реальное исключение из запроса, если оно есть
        Throwable rootCause = findRootCause(request, ex);

        logError("Необработанное исключение", rootCause);
        setErrorAttributes(model,
                "Произошла непредвиденная ошибка: " + rootCause.getMessage(), request);

        return DEFAULT_ERROR_VIEW;
    }

    /**
     * Находит корневую причину исключения.
     *
     * @param request HTTP-запрос
     * @param ex исключение
     * @return корневая причина исключения
     */
    private Throwable findRootCause(HttpServletRequest request, Exception ex) {
        return Optional.ofNullable((Throwable) request.getAttribute("javax.servlet.error.exception"))
                .orElse(ex);
    }

    /**
     * Логирует информацию об ошибке.
     *
     * @param errorType тип ошибки
     * @param ex исключение
     */
    private void logError(String errorType, Throwable ex) {
        log.error("{}: {}", errorType, ex.getMessage(), ex);
    }

    /**
     * Устанавливает атрибуты для страницы ошибки.
     *
     * @param model модель представления
     * @param errorMessage сообщение об ошибке
     * @param request HTTP-запрос
     */
    private void setErrorAttributes(Model model, String errorMessage, HttpServletRequest request) {
        model.addAttribute(ERROR_MESSAGE_ATTR, errorMessage);
        model.addAttribute(REQUEST_URI_ATTR, request.getRequestURI());
        model.addAttribute(TIMESTAMP_ATTR, System.currentTimeMillis());
    }
}