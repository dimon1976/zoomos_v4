package com.java;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;


@SpringBootApplication
@EnableScheduling
@Slf4j
public class Main {
    private final Environment environment;

    public Main(Environment environment) {
        this.environment = environment;
    }

    /**
     * Точка входа в приложение
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    /**
     * Вызывается после полного запуска приложения.
     * Логирует информацию о запущенном окружении.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logApplicationStartup() {
        String activeProfiles = Arrays.toString(environment.getActiveProfiles());
        String port = environment.getProperty("server.port", "8081");
        String appName = environment.getProperty("spring.application.name", "Обработка файлов");

        log.info("----------------------------------------------------------");
        log.info("  Приложение '{}' запущено!", appName);
        log.info("  Профиль(и): {}", activeProfiles);
        log.info("  Web URL: http://localhost:{}", port);
        log.info("  Дашборд: http://localhost:{}/", port);
        log.info("----------------------------------------------------------");
    }
}