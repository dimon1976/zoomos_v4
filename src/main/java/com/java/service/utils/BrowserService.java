package com.java.service.utils;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Сервис для работы с браузером через Selenium
 */
@Service
@Slf4j
public class BrowserService {

    /**
     * Результат браузерной навигации
     */
    public static class BrowserResult {
        private String finalUrl;
        private String status;
        private String errorMessage;
        private int redirectCount;

        public BrowserResult(String finalUrl, String status, String errorMessage, int redirectCount) {
            this.finalUrl = finalUrl;
            this.status = status;
            this.errorMessage = errorMessage;
            this.redirectCount = redirectCount;
        }

        public String getFinalUrl() { return finalUrl; }
        public String getStatus() { return status; }
        public String getErrorMessage() { return errorMessage; }
        public int getRedirectCount() { return redirectCount; }
    }

    /**
     * Получение финального URL с помощью браузера
     */
    public BrowserResult getUrlWithBrowser(String originalUrl, int timeoutSeconds) {
        WebDriver driver = null;
        String initialUrl = originalUrl;
        
        try {
            log.info("Запуск браузера для URL: {}", originalUrl);
            
            // Создаем и настраиваем драйвер
            driver = createWebDriver(timeoutSeconds);
            
            // Переходим на страницу
            log.info("Загружаем страницу: {}", originalUrl);
            driver.get(originalUrl);
            String currentUrl = originalUrl;
            int redirectCount = 0;
            
            // Ждем полной загрузки страницы
            Thread.sleep(20000);
            log.info("Проверяем URL после начальной загрузки: {}", driver.getCurrentUrl());
            
            // Пытаемся найти и выполнить возможные JavaScript редиректы
            try {
                // Проверяем мета-теги редиректов
                Object metaRedirect = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "return document.querySelector('meta[http-equiv=\"refresh\"]')?.getAttribute('content');"
                );
                if (metaRedirect != null) {
                    log.info("Найден meta-redirect: {}", metaRedirect);
                }
                
                // Проверяем JavaScript редиректы
                Object jsRedirect = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "return window.location.href;"
                );
                log.info("JavaScript location: {}", jsRedirect);
                
                // Принудительно выполняем возможные отложенные скрипты
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "setTimeout(function() { console.log('Force JS execution'); }, 100);"
                );
                
            } catch (Exception jsEx) {
                log.warn("Ошибка при выполнении JavaScript проверки: {}", jsEx.getMessage());
            }
            
            // Ждём и отслеживаем изменения URL с максимум 2 попытками
            for (int attempt = 1; attempt <= 2; attempt++) {
                log.info("Попытка {}: ожидаем редирект для {}", attempt, currentUrl);
                
                // Время ожидания: 3s, 5s
                int waitTime = attempt == 1 ? 3000 : 5000;
                
                Thread.sleep(waitTime);
                
                String newUrl = driver.getCurrentUrl();
                log.info("Текущий URL после ожидания: {}", newUrl);
                
                if (!newUrl.equals(currentUrl)) {
                    log.info("*** ОБНАРУЖЕН РЕДИРЕКТ: {} -> {}", currentUrl, newUrl);
                    currentUrl = newUrl;
                    redirectCount++;
                    
                    // Дополнительно ждём после редиректа на случай цепочки редиректов
                    Thread.sleep(3000);
                    
                    // Проверяем ещё раз
                    String finalCheck = driver.getCurrentUrl();
                    if (!finalCheck.equals(currentUrl)) {
                        log.info("*** ДОПОЛНИТЕЛЬНЫЙ РЕДИРЕКТ: {} -> {}", currentUrl, finalCheck);
                        currentUrl = finalCheck;
                        redirectCount++;
                    }
                    break;
                }
                
                // На второй попытке дополнительно проверяем JavaScript
                if (attempt == 2) {
                    try {
                        Object currentJsUrl = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                            "return window.location.href;"
                        );
                        if (!currentJsUrl.equals(currentUrl)) {
                            log.info("*** JS URL отличается: {} -> {}", currentUrl, currentJsUrl);
                            currentUrl = (String) currentJsUrl;
                            redirectCount++;
                            break;
                        }
                    } catch (Exception e) {
                        log.debug("Не удалось проверить JS URL: {}", e.getMessage());
                    }
                }
            }
            
            String finalUrl = currentUrl;
            
            log.info("Браузер завершил обработку: {} -> {}", originalUrl, finalUrl);
            
            return new BrowserResult(finalUrl, "SUCCESS", null, redirectCount);
            
        } catch (Exception e) {
            log.error("Ошибка при работе с браузером для URL {}: {}", originalUrl, e.getMessage());
            return new BrowserResult(originalUrl, "ERROR", e.getMessage(), 0);
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("Ошибка при закрытии браузера: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Создание и настройка WebDriver
     */
    private WebDriver createWebDriver(int timeoutSeconds) {
        try {
            // Пытаемся использовать Chrome (предпочтительно)
            return createChromeDriver(timeoutSeconds);
        } catch (Exception e) {
            log.warn("Chrome недоступен, переключаемся на Firefox: {}", e.getMessage());
            try {
                return createFirefoxDriver(timeoutSeconds);
            } catch (Exception ex) {
                log.error("Firefox также недоступен: {}", ex.getMessage());
                throw new RuntimeException("Не удалось запустить ни один браузер", ex);
            }
        }
    }

    /**
     * Создание Chrome драйвера
     */
    private WebDriver createChromeDriver(int timeoutSeconds) {
        WebDriverManager.chromedriver().setup();
        
        ChromeOptions options = new ChromeOptions();

        // === КЛЮЧЕВЫЕ ОПЦИИ ДЛЯ МАСКИРОВКИ ===
        // 1. Отключаем флаг автоматизации, который показывает плашку "браузером управляет..."
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        // 2. Отключаем Blink-фичу, которая позволяет сайтам определять автоматизацию
        options.addArguments("--disable-blink-features=AutomationControlled");
        // 3. Устанавливаем "человеческий" User-Agent
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // === ДОПОЛНИТЕЛЬНЫЕ ОПЦИИ ДЛЯ СТАБИЛЬНОСТИ И СОВМЕСТИМОСТИ ===
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--allow-running-insecure-content");
        options.addArguments("--disable-web-security");
        
        // Включаем headless режим для фоновой работы
        // options.addArguments("--headless");

        ChromeDriver driver = new ChromeDriver(options);
        
        // Устанавливаем таймауты
        driver.manage().timeouts()
                .pageLoadTimeout(Duration.ofSeconds(timeoutSeconds))
                .implicitlyWait(Duration.ofSeconds(10))
                .scriptTimeout(Duration.ofSeconds(30));
        
        return driver;
    }

    /**
     * Создание Firefox драйвера
     */
    private WebDriver createFirefoxDriver(int timeoutSeconds) {
        WebDriverManager.firefoxdriver().setup();
        
        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("--headless"); // Запуск в фоновом режиме
        options.addArguments("--width=1920");
        options.addArguments("--height=1080");
        
        // Настройки профиля Firefox
        options.addPreference("general.useragent.override", 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:91.0) Gecko/20100101 Firefox/91.0");
        
        FirefoxDriver driver = new FirefoxDriver(options);
        
        // Устанавливаем таймауты
        driver.manage().timeouts()
                .pageLoadTimeout(Duration.ofSeconds(timeoutSeconds))
                .implicitlyWait(Duration.ofSeconds(5));
        
        return driver;
    }

    /**
     * Проверка доступности браузеров
     */
    public boolean isBrowserAvailable() {
        try {
            WebDriver testDriver = createWebDriver(5);
            testDriver.quit();
            return true;
        } catch (Exception e) {
            log.warn("Браузеры недоступны: {}", e.getMessage());
            return false;
        }
    }
}
