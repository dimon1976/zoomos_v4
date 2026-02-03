package com.java.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация proxy серверов для обхода региональных блокировок
 * в HTTP Redirect Finder утилите
 */
@Configuration
@ConfigurationProperties(prefix = "redirect.proxy")
@Data
public class ProxyConfig {

    /**
     * Включить использование proxy
     */
    private boolean enabled = false;

    /**
     * Адрес proxy сервера (формат: host:port)
     */
    private String server;

    /**
     * Имя пользователя для аутентификации proxy
     */
    private String username;

    /**
     * Пароль для аутентификации proxy
     */
    private String password;

    /**
     * Тип proxy сервера
     */
    private ProxyType type = ProxyType.HTTP;

    /**
     * Настройки ротации proxy
     */
    private Rotating rotating = new Rotating();

    /**
     * Вложенный класс настроек для rotating proxies
     */
    @Data
    public static class Rotating {
        /**
         * Включить ротацию proxy (использовать пул)
         */
        private boolean enabled = false;

        /**
         * Размер пула proxy серверов
         */
        private int poolSize = 5;

        /**
         * Путь к файлу со списком proxy серверов
         * Формат файла: host:port:username:password (один на строку)
         */
        private String poolFile = "data/config/proxy-list.txt";
    }

    /**
     * Типы поддерживаемых proxy серверов
     */
    public enum ProxyType {
        /**
         * HTTP/HTTPS proxy
         */
        HTTP,

        /**
         * SOCKS5 proxy
         */
        SOCKS5
    }
}
