package com.java.service.utils;

public enum PageStatus {
    SUCCESS,              // Успешно получен финальный URL
    REDIRECT,             // Промежуточный редирект (внутренний статус, не возвращается)
    MAX_REDIRECTS,        // Достигнут лимит редиректов
    TIMEOUT,              // Таймаут соединения или чтения
    BLOCKED_TIMEOUT,      // Заблокировано по таймауту
    NOT_FOUND,            // Ошибка 404
    FORBIDDEN,            // Ошибка 401 или 403
    BLOCKED_FORBIDDEN,    // Заблокировано (401, 403, 429)
    BLOCKED_CONTENT,      // Заблокировано по содержимому (captcha, cloudflare)
    ERROR,                // Любая другая ошибка (включая 5xx)
    UNKNOWN_HOST,         // Не удалось разрешить доменное имя
    CONNECTION_REFUSED,   // Соединение отклонено
    IO_ERROR,             // Ошибка ввода-вывода
    BROWSER_ERROR         // Ошибка при работе с эмуляцией браузера
}
