package com.java.exception;

import java.util.List;

/**
 * Исключение валидации шаблона
 */
public class TemplateValidationException extends ImportException {

    private final List<String> errors;

    public TemplateValidationException(String message, List<String> errors) {
        super("template.validation.error", message);
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}