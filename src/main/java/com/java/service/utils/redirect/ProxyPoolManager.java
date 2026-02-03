package com.java.service.utils.redirect;

import com.java.config.ProxyConfig;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Управление пулом proxy серверов для ротации IP-адресов
 * Поддерживает загрузку из файла и round-robin выборку
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProxyPoolManager {

    private final ProxyConfig proxyConfig;

    /**
     * Потокобезопасный список proxy серверов
     */
    private final List<ProxyServer> proxyPool = new CopyOnWriteArrayList<>();

    /**
     * Счетчик для round-robin ротации
     */
    private final AtomicInteger currentIndex = new AtomicInteger(0);

    /**
     * Инициализация пула proxy при старте приложения
     */
    @PostConstruct
    public void initializePool() {
        if (!proxyConfig.getRotating().isEnabled()) {
            log.info("Ротация proxy отключена");
            return;
        }

        try {
            loadProxyServersFromFile();
            if (proxyPool.isEmpty()) {
                log.warn("Пул proxy серверов пуст. Ротация не будет работать.");
            } else {
                log.info("Инициализирован пул из {} proxy серверов", proxyPool.size());
            }
        } catch (Exception e) {
            log.error("Ошибка инициализации пула proxy", e);
        }
    }

    /**
     * Загрузка списка proxy серверов из файла
     * Формат файла: host:port:username:password (один на строку)
     * Пустые строки и строки начинающиеся с # игнорируются
     */
    private void loadProxyServersFromFile() throws IOException {
        String poolFile = proxyConfig.getRotating().getPoolFile();
        Path filePath = Paths.get(poolFile);

        if (!Files.exists(filePath)) {
            log.warn("Файл с proxy серверами не найден: {}", poolFile);
            return;
        }

        List<String> lines = Files.readAllLines(filePath);
        int loaded = 0;

        for (String line : lines) {
            line = line.trim();

            // Пропускаем пустые строки и комментарии
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            try {
                ProxyServer server = parseProxyLine(line);
                if (server != null) {
                    proxyPool.add(server);
                    loaded++;
                }
            } catch (Exception e) {
                log.warn("Ошибка парсинга строки proxy: {}", line, e);
            }
        }

        log.info("Загружено {} proxy серверов из файла {}", loaded, poolFile);
    }

    /**
     * Парсинг одной строки с proxy сервером
     * Формат: host:port или host:port:username:password
     */
    private ProxyServer parseProxyLine(String line) {
        String[] parts = line.split(":");

        if (parts.length < 2) {
            log.warn("Некорректный формат proxy: {}. Ожидается host:port[:username:password]", line);
            return null;
        }

        String host = parts[0].trim();
        int port;

        try {
            port = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            log.warn("Некорректный порт в proxy: {}", line);
            return null;
        }

        // Опциональные username и password
        String username = parts.length > 2 ? parts[2].trim() : null;
        String password = parts.length > 3 ? parts[3].trim() : null;

        return new ProxyServer(host, port, username, password);
    }

    /**
     * Получить следующий proxy из пула (round-robin)
     * @return ProxyServer или null если пул пуст
     */
    public ProxyServer getNextProxy() {
        if (proxyPool.isEmpty()) {
            log.warn("Пул proxy пуст. Возвращаем null.");
            return null;
        }

        int index = currentIndex.getAndIncrement() % proxyPool.size();
        ProxyServer proxy = proxyPool.get(index);

        log.debug("Выбран proxy #{}: {}:{}", index + 1, proxy.getHost(), proxy.getPort());

        return proxy;
    }

    /**
     * Получить общее количество proxy в пуле
     */
    public int getPoolSize() {
        return proxyPool.size();
    }

    /**
     * Проверить пуст ли пул
     */
    public boolean isEmpty() {
        return proxyPool.isEmpty();
    }

    /**
     * Модель данных proxy сервера
     */
    @Data
    @AllArgsConstructor
    public static class ProxyServer {
        /**
         * Хост proxy сервера
         */
        private String host;

        /**
         * Порт proxy сервера
         */
        private int port;

        /**
         * Имя пользователя (опционально)
         */
        private String username;

        /**
         * Пароль (опционально)
         */
        private String password;

        /**
         * Проверить требуется ли аутентификация
         */
        public boolean requiresAuth() {
            return username != null && !username.isEmpty() &&
                   password != null && !password.isEmpty();
        }

        @Override
        public String toString() {
            return host + ":" + port + (requiresAuth() ? " (auth)" : "");
        }
    }
}
