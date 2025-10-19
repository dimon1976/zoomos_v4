package com.java.service.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Сервис для получения интернационализированных сообщений
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageSource messageSource;

    /**
     * Получить сообщение по ключу с текущей локалью без параметров
     */
    public String get(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    /**
     * Получить сообщение по ключу с параметрами и текущей локалью
     */
    public String get(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    /**
     * Получить сообщение по ключу с указанной локалью
     */
    public String getWithLocale(String code, Locale locale) {
        return messageSource.getMessage(code, null, locale);
    }

    /**
     * Получить сообщение по ключу с параметрами и указанной локалью
     */
    public String getWithLocale(String code, Locale locale, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }

    /**
     * Получить сообщение с fallback значением если ключ не найден
     */
    public String getMessageOrDefault(String code, String defaultMessage) {
        return messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale());
    }
}
