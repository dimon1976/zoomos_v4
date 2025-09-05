package com.java.service.utils;

import com.java.dto.utils.RedirectFinderDto;
import com.java.model.entity.FileMetadata;
import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import com.java.service.utils.redirect.CurlStrategy;
import com.java.service.utils.redirect.HttpClientStrategy;
import com.java.service.utils.redirect.RedirectStrategy;
import com.java.service.exports.FileGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Интеграционные тесты для RedirectFinderService
 */
@ExtendWith(MockitoExtension.class)
class RedirectFinderServiceTest {

    @Mock
    private FileGeneratorService fileGeneratorService;
    
    @Mock
    private RedirectStrategy mockStrategy;
    
    private RedirectFinderService redirectFinderService;
    private CurlStrategy curlStrategy;
    private HttpClientStrategy httpClientStrategy;

    @BeforeEach
    void setUp() {
        curlStrategy = new CurlStrategy();
        httpClientStrategy = new HttpClientStrategy();
        
        // Используем реальные стратегии для интеграционного тестирования
        redirectFinderService = new RedirectFinderService(
            List.of(curlStrategy, httpClientStrategy),
            fileGeneratorService
        );
    }

    @Test
    void testProcessRedirectFinding_WithCsvFile_Success() throws IOException {
        // Given
        Path tempFile = createTestCsvFile();
        FileMetadata metadata = FileMetadata.builder()
            .originalFilename("test_redirect.csv")
            .tempFilePath(tempFile.toString())
            .fileSize(Files.size(tempFile))
            .build();

        RedirectFinderDto dto = new RedirectFinderDto();
        dto.setUrlColumn(1); // Вторая колонка (URL)
        dto.setIdColumn(0);  // Первая колонка (ID)
        dto.setMaxRedirects(5);
        dto.setTimeoutMs(5000);
        dto.setOutputFormat("csv");

        // When
        byte[] result = redirectFinderService.processRedirectFinding(metadata, dto);

        // Then
        assertThat(result).isNotNull();
        String csvContent = new String(result);
        assertThat(csvContent).contains("ID,Модель,Исходный URL,Финальный URL");
        // CSV должен содержать заголовки и данные
        
        // Cleanup
        Files.deleteIfExists(tempFile);
    }

    @Test
    void testProcessRedirectFinding_WithInvalidColumnIndex_ThrowsException() throws IOException {
        // Given
        Path tempFile = createTestCsvFile();
        FileMetadata metadata = FileMetadata.builder()
            .originalFilename("test.csv")
            .tempFilePath(tempFile.toString())
            .fileSize(Files.size(tempFile))
            .build();

        RedirectFinderDto dto = new RedirectFinderDto();
        dto.setUrlColumn(10); // Несуществующая колонка
        dto.setMaxRedirects(5);
        dto.setTimeoutMs(5000);

        // When & Then
        assertThatThrownBy(() -> redirectFinderService.processRedirectFinding(metadata, dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Указанная колонка URL");
        
        // Cleanup
        Files.deleteIfExists(tempFile);
    }

    @Test
    void testProcessRedirectFinding_WithEmptyFile_ThrowsException() throws IOException {
        // Given
        Path tempFile = createEmptyFile();
        FileMetadata metadata = FileMetadata.builder()
            .originalFilename("empty.csv")
            .tempFilePath(tempFile.toString())
            .fileSize(0L)
            .build();

        RedirectFinderDto dto = new RedirectFinderDto();
        dto.setUrlColumn(0);

        // When & Then
        assertThatThrownBy(() -> redirectFinderService.processRedirectFinding(metadata, dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("не содержит данных");
        
        // Cleanup
        Files.deleteIfExists(tempFile);
    }

    @Test
    void testValidateColumns_WithValidColumns_Success() throws IOException {
        // Given
        Path tempFile = createTestCsvFile();
        FileMetadata metadata = FileMetadata.builder()
            .tempFilePath(tempFile.toString())
            .build();

        RedirectFinderDto dto = new RedirectFinderDto();
        dto.setUrlColumn(1);
        dto.setIdColumn(0);

        // When - no exception should be thrown
        assertThatCode(() -> redirectFinderService.processRedirectFinding(metadata, dto))
            .doesNotThrowAnyException();
        
        // Cleanup
        Files.deleteIfExists(tempFile);
    }

    @Test
    void testProcessUrlsWithStrategies_RealUrlRedirects() {
        // Given - создаем результат как будто стратегия обработала реальный URL
        // Тестируем реальную стратегию напрямую

        // Можем протестировать стратегию напрямую
        RedirectResult actualResult = curlStrategy.followRedirects(
            "https://www.google.com", 5, 5000
        );

        // Then
        assertThat(actualResult).isNotNull();
        assertThat(actualResult.getOriginalUrl()).isEqualTo("https://www.google.com");
        assertThat(actualResult.getStatus()).isIn(PageStatus.OK, PageStatus.REDIRECT);
        assertThat(actualResult.getFinalUrl()).isNotNull().isNotEmpty();
        assertThat(actualResult.getProcessingTimeMs()).isPositive();
        assertThat(actualResult.getStrategy()).isEqualTo("CurlStrategy");
    }

    @Test
    void testProcessUrlsWithStrategies_ErrorHandling() {
        // Given - тестируем с недоступным URL
        RedirectResult result = curlStrategy.followRedirects(
            "http://nonexistent-domain-12345.com", 5, 2000
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PageStatus.ERROR);
        assertThat(result.getErrorMessage()).isNotNull();
        assertThat(result.getFinalUrl()).isEqualTo("http://nonexistent-domain-12345.com"); // Должен остаться исходный URL
    }

    @Test
    void testStrategySelection_CurlFirst_ThenHttpClient() {
        // Проверяем что стратегии отсортированы по приоритету
        List<RedirectStrategy> strategies = List.of(curlStrategy, httpClientStrategy);
        
        // CurlStrategy должна иметь более высокий приоритет (меньшее число)
        assertThat(curlStrategy.getPriority()).isLessThan(httpClientStrategy.getPriority());
        
        // Проверяем что обе стратегии могут обработать обычный URL
        assertThat(curlStrategy.canHandle("https://google.com", null)).isTrue();
        assertThat(httpClientStrategy.canHandle("https://google.com", null)).isTrue();
    }

    @Test
    void testProcessFileWithBothIdAndModelColumns() throws IOException {
        // Given
        Path tempFile = createTestCsvFileWithModelColumn();
        FileMetadata metadata = FileMetadata.builder()
            .originalFilename("test_with_model.csv")
            .tempFilePath(tempFile.toString())
            .fileSize(Files.size(tempFile))
            .build();

        RedirectFinderDto dto = new RedirectFinderDto();
        dto.setUrlColumn(2); // Третья колонка (URL)
        dto.setIdColumn(0);  // Первая колонка (ID)
        dto.setModelColumn(1); // Вторая колонка (Model)
        dto.setMaxRedirects(3);
        dto.setTimeoutMs(3000);

        // When
        byte[] result = redirectFinderService.processRedirectFinding(metadata, dto);

        // Then
        assertThat(result).isNotNull();
        String csvContent = new String(result);
        assertThat(csvContent).contains("ID,Модель,Исходный URL,Финальный URL");
        
        // Cleanup
        Files.deleteIfExists(tempFile);
    }

    // Helper methods

    private Path createTestCsvFile() throws IOException {
        Path tempFile = Files.createTempFile("test_redirect", ".csv");
        String csvContent = "id,url\n" +
                           "1,https://www.google.com\n" +
                           "2,https://github.com\n";
        Files.write(tempFile, csvContent.getBytes());
        return tempFile;
    }

    private Path createTestCsvFileWithModelColumn() throws IOException {
        Path tempFile = Files.createTempFile("test_redirect_model", ".csv");
        String csvContent = "id,model,url\n" +
                           "1,ProductA,https://www.google.com\n" +
                           "2,ProductB,https://github.com\n";
        Files.write(tempFile, csvContent.getBytes());
        return tempFile;
    }

    private Path createEmptyFile() throws IOException {
        return Files.createTempFile("empty", ".csv");
    }
}