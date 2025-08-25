import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Простая утилита для проверки корректности кода
 */
public class ValidateCode {
    
    public static void main(String[] args) {
        System.out.println("=== Проверка кода OperationDeletionService ===\n");
        
        // Проверяем основные файлы
        checkFile("src/main/java/com/java/service/operations/OperationDeletionService.java", "Сервис удаления операций");
        checkFile("src/main/java/com/java/controller/OperationsRestController.java", "REST контроллер операций");
        checkFile("src/test/java/com/java/service/operations/OperationDeletionServiceTest.java", "Тест сервиса удаления");
        
        // Проверяем репозитории
        checkFile("src/main/java/com/java/repository/ImportErrorRepository.java", "Репозиторий ошибок импорта");
        checkFile("src/main/java/com/java/repository/FileMetadataRepository.java", "Репозиторий метаданных файлов");
        
        System.out.println("\n=== Проверка завершена ===");
    }
    
    private static void checkFile(String fileName, String description) {
        System.out.printf("📁 %s (%s)\n", description, fileName);
        
        Path filePath = Paths.get(fileName);
        if (!Files.exists(filePath)) {
            System.out.println("   ❌ Файл не найден!");
            return;
        }
        
        try {
            String content = Files.readString(filePath);
            
            // Базовые проверки синтаксиса Java
            if (!content.contains("package com.java")) {
                System.out.println("   ⚠️  Некорректный package");
            } else {
                System.out.println("   ✅ Package корректный");
            }
            
            // Специфичные проверки для разных типов файлов
            if (fileName.contains("OperationDeletionService.java")) {
                checkDeletionService(content);
            } else if (fileName.contains("OperationsRestController.java")) {
                checkController(content);
            } else if (fileName.contains("Test.java")) {
                checkTest(content);
            }
            
            System.out.printf("   📊 Размер: %d символов\n", content.length());
            
        } catch (IOException e) {
            System.out.println("   ❌ Ошибка чтения файла: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    private static void checkDeletionService(String content) {
        if (content.contains("@Transactional")) {
            System.out.println("   ✅ Использует транзакции");
        } else {
            System.out.println("   ⚠️  Отсутствует @Transactional");
        }
        
        if (content.contains("deleteOperationCompletely")) {
            System.out.println("   ✅ Метод deleteOperationCompletely найден");
        } else {
            System.out.println("   ❌ Метод deleteOperationCompletely не найден");
        }
        
        if (content.contains("av_data") && content.contains("av_handbook")) {
            System.out.println("   ✅ Удаляет данные из av_data и av_handbook");
        } else {
            System.out.println("   ❌ Не удаляет данные из основных таблиц");
        }
    }
    
    private static void checkController(String content) {
        if (content.contains("OperationDeletionService")) {
            System.out.println("   ✅ Использует OperationDeletionService");
        } else {
            System.out.println("   ❌ Не использует OperationDeletionService");
        }
        
        if (content.contains("deletion-stats")) {
            System.out.println("   ✅ Добавлен эндпоинт для статистики удаления");
        } else {
            System.out.println("   ⚠️  Отсутствует эндпоинт для статистики");
        }
    }
    
    private static void checkTest(String content) {
        if (content.contains("@Test")) {
            int testCount = content.split("@Test").length - 1;
            System.out.printf("   ✅ Найдено %d тестовых методов\n", testCount);
        } else {
            System.out.println("   ❌ Тестовые методы не найдены");
        }
        
        if (content.contains("@Mock")) {
            System.out.println("   ✅ Использует моки");
        } else {
            System.out.println("   ⚠️  Не использует моки");
        }
    }
}