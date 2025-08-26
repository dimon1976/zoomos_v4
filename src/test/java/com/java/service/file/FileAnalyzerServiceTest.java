package com.java.service.file;

import com.java.config.ImportConfig;
import com.java.model.entity.FileMetadata;
import com.java.util.PathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Тесты для FileAnalyzerService с акцентом на обработку специальных символов в заголовках
 */
@ExtendWith(MockitoExtension.class)
class FileAnalyzerServiceTest {

    @Mock
    private PathResolver pathResolver;
    
    @Mock
    private ImportConfig.ImportSettings importSettings;

    private FileAnalyzerService fileAnalyzerService;

    @BeforeEach
    void setUp() {
        when(importSettings.getSampleRows()).thenReturn(5);
        fileAnalyzerService = new FileAnalyzerService(pathResolver, importSettings);
    }

    @Test
    void testBackslashInCsvHeaderIsPreserved() throws IOException {
        // Подготавливаем тестовые данные с обратным слэшем в заголовке
        String csvContent = "Цена акционная\\по карте,Название товара,Количество\n" +
                           "100.50,\"Хлеб белый\",5\n" +
                           "250.00,\"Молоко 3.2%\",3\n";
        
        // Создаем временный файл
        Path tempFile = Files.createTempFile("test_backslash", ".csv");
        Files.write(tempFile, csvContent.getBytes(StandardCharsets.UTF_8));
        
        try {
            when(pathResolver.getFileSize(tempFile)).thenReturn((long) csvContent.getBytes().length);
            
            // Анализируем файл
            FileMetadata metadata = fileAnalyzerService.analyzeFile(tempFile, "test_backslash.csv");
            
            // Проверяем, что заголовки корректно считаны
            assertNotNull(metadata);
            assertTrue(metadata.getHasHeader());
            assertEquals(3, metadata.getTotalColumns());
            
            // Парсим заголовки из JSON
            String columnHeadersJson = metadata.getColumnHeaders();
            assertNotNull(columnHeadersJson);
            assertTrue(columnHeadersJson.contains("Цена акционная\\\\по карте"), 
                      "Заголовок должен сохранить обратный слэш как обычный символ, но получили: " + columnHeadersJson);
            
            System.out.println("Заголовки из метаданных: " + columnHeadersJson);
            
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testMultipleBackslashesInHeaders() throws IOException {
        // Тест с несколькими обратными слэшами
        String csvContent = "Путь\\к\\файлу,Размер\\в\\байтах,Дата\\создания\n" +
                           "\"/home/user/doc.txt\",1024,\"2024-01-01\"\n" +
                           "\"/var/log/app.log\",2048,\"2024-01-02\"\n";
        
        Path tempFile = Files.createTempFile("test_multiple_backslash", ".csv");
        Files.write(tempFile, csvContent.getBytes(StandardCharsets.UTF_8));
        
        try {
            when(pathResolver.getFileSize(tempFile)).thenReturn((long) csvContent.getBytes().length);
            
            FileMetadata metadata = fileAnalyzerService.analyzeFile(tempFile, "test_multiple_backslash.csv");
            
            assertNotNull(metadata);
            assertTrue(metadata.getHasHeader());
            assertEquals(3, metadata.getTotalColumns());
            
            String columnHeadersJson = metadata.getColumnHeaders();
            assertNotNull(columnHeadersJson);
            assertTrue(columnHeadersJson.contains("Путь\\\\к\\\\файлу"));
            assertTrue(columnHeadersJson.contains("Размер\\\\в\\\\байтах"));  
            assertTrue(columnHeadersJson.contains("Дата\\\\создания"));
            
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testBackslashWithQuotes() throws IOException {
        // Тест с обратными слэшами в кавычках
        String csvContent = "\"Цена\\с НДС\",\"Путь\\в системе\",Количество\n" +
                           "\"150.75\",\"/home/user\",\"10\"\n" +
                           "\"200.00\",\"/var/data\",\"25\"\n";
        
        Path tempFile = Files.createTempFile("test_backslash_quotes", ".csv");
        Files.write(tempFile, csvContent.getBytes(StandardCharsets.UTF_8));
        
        try {
            when(pathResolver.getFileSize(tempFile)).thenReturn((long) csvContent.getBytes().length);
            
            FileMetadata metadata = fileAnalyzerService.analyzeFile(tempFile, "test_backslash_quotes.csv");
            
            assertNotNull(metadata);
            assertTrue(metadata.getHasHeader());
            assertEquals(3, metadata.getTotalColumns());
            
            String columnHeadersJson = metadata.getColumnHeaders();
            assertNotNull(columnHeadersJson);
            assertTrue(columnHeadersJson.contains("Цена\\\\с НДС"));
            assertTrue(columnHeadersJson.contains("Путь\\\\в системе"));
            
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testEscapeCharacterHandling() throws IOException {
        // Тест с символом экранирования
        String csvContent = "Обычный заголовок,\"Заголовок с \\\"кавычками\\\"\",Цена\\по карте\n" +
                           "\"Значение 1\",\"Значение с \\\"кавычками\\\"\",\"100.50\"\n";
        
        Path tempFile = Files.createTempFile("test_escape_chars", ".csv");
        Files.write(tempFile, csvContent.getBytes(StandardCharsets.UTF_8));
        
        try {
            when(pathResolver.getFileSize(tempFile)).thenReturn((long) csvContent.getBytes().length);
            
            FileMetadata metadata = fileAnalyzerService.analyzeFile(tempFile, "test_escape_chars.csv");
            
            assertNotNull(metadata);
            assertTrue(metadata.getHasHeader());
            assertEquals(3, metadata.getTotalColumns());
            
            String columnHeadersJson = metadata.getColumnHeaders();
            System.out.println("Заголовки с escape символами: " + columnHeadersJson);
            
            // Проверяем, что обратный слэш в обычном заголовке сохранился
            assertTrue(columnHeadersJson.contains("Цена\\\\по карте"));
            
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}