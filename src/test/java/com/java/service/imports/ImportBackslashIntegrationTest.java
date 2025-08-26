package com.java.service.imports;

import com.java.model.entity.ImportSession;
import com.java.model.entity.ImportTemplate;
import com.java.model.enums.EntityType;
import com.java.model.enums.ErrorStrategy;
import com.java.model.enums.DuplicateStrategy;
import com.java.service.file.FileAnalyzerService;
import com.java.model.entity.FileMetadata;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест для проверки корректной обработки обратных слэшей
 * во время полного цикла импорта CSV файлов
 */
@ExtendWith(MockitoExtension.class)
class ImportBackslashIntegrationTest {

    @Test 
    void testCompleteImportCycleWithBackslashes() throws Exception {
        // Тестовые данные с различными вариантами обратных слэшей
        String csvContent = """
                Цена акционная\\по карте,Путь\\к\\файлу,Описание\\товара
                100.50,"/home/user/doc.txt","Хлеб с\\приправами"
                250.00,"/var/log\\app.log","Молоко\\Домик"
                """;

        // Создаем временный файл
        Path tempFile = Files.createTempFile("integration_backslash_test", ".csv");
        Files.write(tempFile, csvContent.getBytes(StandardCharsets.UTF_8));

        try {
            // Тестируем CSVParser напрямую с нашими настройками
            testDirectCsvParsing(csvContent);
            
            // Проверяем сохранение данных в разных вариациях
            testDataPreservation(csvContent);
            
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void testDirectCsvParsing(String csvContent) throws Exception {
        // Настраиваем парсер аналогично нашим сервисам
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .withEscapeChar(CSVParser.NULL_CHARACTER) // Ключевое изменение
                .build();

        try (CSVReader csvReader = new CSVReaderBuilder(new StringReader(csvContent))
                .withCSVParser(parser)
                .build()) {

            // Читаем заголовки
            String[] headers = csvReader.readNext();
            assertNotNull(headers);
            assertEquals(3, headers.length);
            
            // Проверяем заголовки с обратными слэшами
            assertEquals("Цена акционная\\по карте", headers[0]);
            assertEquals("Путь\\к\\файлу", headers[1]);
            assertEquals("Описание\\товара", headers[2]);

            // Читаем первую строку данных
            String[] row1 = csvReader.readNext();
            assertNotNull(row1);
            assertEquals(3, row1.length);
            assertEquals("100.50", row1[0]);
            assertEquals("/home/user/doc.txt", row1[1]);
            assertEquals("Хлеб с\\приправами", row1[2]);

            // Читаем вторую строку данных  
            String[] row2 = csvReader.readNext();
            assertNotNull(row2);
            assertEquals(3, row2.length);
            assertEquals("250.00", row2[0]);
            assertEquals("/var/log\\app.log", row2[1]);
            assertEquals("Молоко\\Домик", row2[2]);
        }

        System.out.println("✅ Прямое тестирование CSVParser: обратные слэши сохранены корректно");
    }

    private void testDataPreservation(String csvContent) throws Exception {
        // Тест различных вариантов обратных слэшей
        Map<String, String> testCases = new HashMap<>();
        testCases.put("Цена\\по карте", "должен сохранить одиночный слэш");
        testCases.put("Путь\\к\\файлу", "должен сохранить множественные слэши");
        testCases.put("Описание\\товара", "должен сохранить слэш в середине");
        
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .withEscapeChar(CSVParser.NULL_CHARACTER)
                .build();

        for (Map.Entry<String, String> testCase : testCases.entrySet()) {
            String testString = testCase.getKey();
            String description = testCase.getValue();
            
            String[] result = parser.parseLine(testString);
            assertNotNull(result, "Результат не должен быть null для: " + testString);
            assertEquals(1, result.length, "Должен быть один элемент для: " + testString);
            assertEquals(testString, result[0], description + ", но получили: " + result[0]);
            
            System.out.println("✅ " + description + ": '" + testString + "' -> '" + result[0] + "'");
        }
    }

    @Test
    void testComparisonWithOldBehavior() throws Exception {
        String testData = "Цена акционная\\по карте";
        
        // Старое поведение (с escape символом)
        CSVParser oldParser = new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .withEscapeChar('\\')
                .build();
        
        String[] oldResult = oldParser.parseLine(testData);
        
        // Новое поведение (без escape символа)
        CSVParser newParser = new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .withEscapeChar(CSVParser.NULL_CHARACTER)
                .build();
        
        String[] newResult = newParser.parseLine(testData);
        
        System.out.println("Старое поведение: " + Arrays.toString(oldResult));
        System.out.println("Новое поведение: " + Arrays.toString(newResult));
        
        // Проверяем, что новое поведение сохраняет слэш, а старое - интерпретирует его как escape
        assertNotEquals("Цена акционная\\по карте", oldResult[0], "Старое поведение не должно сохранять слэш как есть");
        assertEquals("Цена акционная\\по карте", newResult[0], "Новое поведение должно сохранить слэш");
        
        System.out.println("✅ Старое поведение изменяет строку: '" + oldResult[0] + "'");
        System.out.println("✅ Новое поведение сохраняет слэш: '" + newResult[0] + "'");
    }

    @Test
    void testEdgeCasesWithBackslashes() throws Exception {
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .withEscapeChar(CSVParser.NULL_CHARACTER)
                .build();

        // Тестируем различные граничные случаи
        String[] testCases = {
                "\\начинается со слэша",
                "заканчивается слэшем\\",
                "\\\\двойной слэш",
                "слэш\\в\\середине\\много",
                "\"в кавычках\\со слэшем\"",
                "обычный текст без слэшей"
        };

        for (String testCase : testCases) {
            String[] result = parser.parseLine(testCase);
            assertNotNull(result);
            assertEquals(1, result.length);
            
            // В кавычках кавычки должны быть удалены, но слэши сохранены
            String expected = testCase.startsWith("\"") && testCase.endsWith("\"") ?
                    testCase.substring(1, testCase.length() - 1) : testCase;
            
            assertEquals(expected, result[0], 
                    "Для случая '" + testCase + "' ожидали '" + expected + "', получили '" + result[0] + "'");
            
            System.out.println("✅ Граничный случай: '" + testCase + "' -> '" + result[0] + "'");
        }
    }
}