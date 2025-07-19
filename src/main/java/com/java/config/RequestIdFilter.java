package com.java.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Фильтр для добавления уникального идентификатора к каждому HTTP-запросу.
 * Улучшает отслеживание запросов в логах путем добавления идентификатора в MDC.
 */
@Component
@Order(1)
@Slf4j
public class RequestIdFilter implements Filter {

    private static final String REQUEST_ID_KEY = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final int REQUEST_ID_LENGTH = 16;

    /**
     * Обрабатывает HTTP-запрос, добавляя к нему уникальный идентификатор.
     *
     * @param request запрос
     * @param response ответ
     * @param chain цепочка фильтров
     * @throws IOException если возникла ошибка ввода-вывода
     * @throws ServletException если возникла ошибка в сервлете
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            // Получаем идентификатор запроса из заголовка или генерируем новый
            String requestId = getOrCreateRequestId((HttpServletRequest) request);
            log.trace("Запрос получил ID: {}", requestId);

            // Добавляем идентификатор запроса в MDC для логирования
            MDC.put(REQUEST_ID_KEY, requestId);

            // Продолжаем цепочку фильтров
            chain.doFilter(request, response);
        } finally {
            // Очищаем MDC после завершения запроса
            MDC.remove(REQUEST_ID_KEY);
            log.trace("MDC очищен после обработки запроса");
        }
    }

    /**
     * Получает идентификатор запроса из заголовка или генерирует новый.
     *
     * @param request HTTP-запрос
     * @return идентификатор запроса
     */
    private String getOrCreateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);

        if (isValidRequestId(requestId)) {
            return requestId;
        }

        return generateRequestId();
    }

    /**
     * Проверяет, является ли идентификатор запроса валидным.
     *
     * @param requestId идентификатор запроса
     * @return true, если идентификатор валиден
     */
    private boolean isValidRequestId(String requestId) {
        return requestId != null && !requestId.isEmpty();
    }

    /**
     * Генерирует новый уникальный идентификатор запроса.
     *
     * @return новый идентификатор запроса
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, REQUEST_ID_LENGTH);
    }
}