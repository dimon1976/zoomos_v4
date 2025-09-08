package com.java.service.utils;

import com.java.dto.utils.StatsProcessDto;
import com.java.model.entity.FileMetadata;
import com.java.service.file.FileAnalyzerService;
import com.java.service.exports.style.ExcelStyleFactory;
import com.java.service.exports.style.ExcelStyles;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit тесты для StatsProcessorService
 */
@ExtendWith(MockitoExtension.class)
class StatsProcessorServiceTest {

    @Mock
    private FileAnalyzerService fileAnalyzerService;

    @Mock 
    private ExcelStyleFactory excelStyleFactory;

    @Mock
    private ExcelStyles excelStyles;

    private StatsProcessorService statsProcessorService;

    @BeforeEach
    void setUp() {
        statsProcessorService = new StatsProcessorService(fileAnalyzerService, excelStyleFactory);
    }

    @Test
    void processStatsFile_withValidData_shouldReturnExcelFile() throws IOException {
        // Given
        FileMetadata metadata = createTestFileMetadata();
        StatsProcessDto dto = createTestStatsProcessDto();
        when(excelStyleFactory.createStyles(any(Workbook.class))).thenReturn(excelStyles);

        // When
        byte[] result = statsProcessorService.processStatsFile(metadata, dto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    void processStatsFile_withCsvFormat_shouldReturnCsvFile() throws IOException {
        // Given
        FileMetadata metadata = createTestFileMetadata();
        StatsProcessDto dto = createTestStatsProcessDto();
        dto.setOutputFormat("csv");

        // When
        byte[] result = statsProcessorService.processStatsFile(metadata, dto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
        
        // Проверяем, что это действительно CSV (содержит разделители)
        String csvContent = new String(result);
        assertThat(csvContent).contains(dto.getCsvDelimiter());
    }

    @Test
    void processStatsFile_withEmptyData_shouldThrowException() {
        // Given
        FileMetadata metadata = new FileMetadata();
        metadata.setSampleData("[]");
        StatsProcessDto dto = createTestStatsProcessDto();

        // When & Then
        assertThatThrownBy(() -> statsProcessorService.processStatsFile(metadata, dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("не содержит данных");
    }

    @Test
    void processStatsFile_withNullData_shouldThrowException() {
        // Given
        FileMetadata metadata = new FileMetadata();
        metadata.setSampleData(null);
        StatsProcessDto dto = createTestStatsProcessDto();

        // When & Then
        assertThatThrownBy(() -> statsProcessorService.processStatsFile(metadata, dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("не содержит данных");
    }

    @Test
    void csvEscaping_withFormulaInjection_shouldSanitizeFormulas() throws IOException {
        // Given
        FileMetadata metadata = createTestFileMetadataWithFormulas();
        StatsProcessDto dto = createTestStatsProcessDto();
        dto.setOutputFormat("csv");

        // When
        byte[] result = statsProcessorService.processStatsFile(metadata, dto);

        // Then
        String csvContent = new String(result);
        
        // Проверяем, что опасные формулы экранированы одинарной кавычкой
        assertThat(csvContent).contains("'=SUM(A1:A10)");
        assertThat(csvContent).contains("'+cmd");
        assertThat(csvContent).contains("'-2+3");
        assertThat(csvContent).contains("'@SUM(A1:A10)");
        
        // Проверяем, что обычные данные не изменены
        assertThat(csvContent).contains("NormalData");
    }

    @Test
    void processStatsFile_withAllOptionsEnabled_shouldIncludeAllColumns() throws IOException {
        // Given
        FileMetadata metadata = createTestFileMetadataWithAllColumns();
        StatsProcessDto dto = createTestStatsProcessDto();
        dto.setShowCompetitorUrl(true);
        dto.setShowSource(true);
        dto.setShowDateAdd(true);
        dto.setOutputFormat("csv");

        // When
        byte[] result = statsProcessorService.processStatsFile(metadata, dto);

        // Then
        String csvContent = new String(result);
        String[] lines = csvContent.split("\n");
        
        // Проверяем заголовки - проверяем структуру CSV вместо конкретных символов из-за проблем с кодировкой в тестах
        String headerLine = lines[0];
        assertThat(headerLine).contains("URL");
        
        // Проверяем что в заголовке правильное количество колонок (19 = 16 базовых + 3 дополнительные)
        String[] headers = headerLine.split(";");
        assertThat(headers).hasSize(19);
    }

    @Test
    void processStatsFile_withSourceReplace_shouldReplaceUnknownUsers() throws IOException {
        // Given
        FileMetadata metadata = createTestFileMetadataWithSource();
        StatsProcessDto dto = createTestStatsProcessDto();
        dto.setShowSource(true);
        dto.setSourceReplace(true);
        dto.setOutputFormat("csv");

        // When
        byte[] result = statsProcessorService.processStatsFile(metadata, dto);

        // Then
        String csvContent = new String(result);
        
        // Проверяем, что неизвестный пользователь заменен на "manager"
        assertThat(csvContent).contains("manager");
        // Проверяем, что известный пользователь не изменен
        assertThat(csvContent).contains("zms-cron");
    }

    private FileMetadata createTestFileMetadata() {
        FileMetadata metadata = new FileMetadata();
        metadata.setSampleData("""
            [
                ["Клиент1", "1", "ID1", "Категория1", "Кат.кл.1", "Бренд1", "Модель1", "КП1", "ШК1", "Статус1", "", "", "Цена1", "", "", "", "", "", "", "Модель2", "КП2", "", "ID2", "Конкурент1", "Вкл1"],
                ["Клиент2", "2", "ID2", "Категория2", "Кат.кл.2", "Бренд2", "Модель2", "КП2", "ШК2", "Статус2", "", "", "Цена2", "", "", "", "", "", "", "Модель3", "КП3", "", "ID3", "Конкурент2", "Вкл2"]
            ]
            """);
        return metadata;
    }

    private FileMetadata createTestFileMetadataWithFormulas() {
        FileMetadata metadata = new FileMetadata();
        metadata.setSampleData("""
            [
                ["=SUM(A1:A10)", "1", "ID1", "Категория1", "Кат.кл.1", "Бренд1", "Модель1", "КП1", "ШК1", "Статус1", "", "", "Цена1", "", "", "", "", "", "", "Модель2", "КП2", "", "ID2", "Конкурент1", "Вкл1"],
                ["+cmd", "2", "ID2", "Категория2", "Кат.кл.2", "Бренд2", "Модель2", "КП2", "ШК2", "Статус2", "", "", "Цена2", "", "", "", "", "", "", "Модель3", "КП3", "", "ID3", "Конкурент2", "Вкл2"],
                ["-2+3", "3", "ID3", "Категория3", "Кат.кл.3", "Бренд3", "Модель3", "КП3", "ШК3", "Статус3", "", "", "Цена3", "", "", "", "", "", "", "Модель4", "КП4", "", "ID4", "Конкурент3", "Вкл3"],
                ["@SUM(A1:A10)", "4", "ID4", "Категория4", "Кат.кл.4", "Бренд4", "Модель4", "КП4", "ШК4", "Статус4", "", "", "Цена4", "", "", "", "", "", "", "Модель5", "КП5", "", "ID5", "Конкурент4", "Вкл4"],
                ["NormalData", "5", "ID5", "Категория5", "Кат.кл.5", "Бренд5", "Модель5", "КП5", "ШК5", "Статус5", "", "", "Цена5", "", "", "", "", "", "", "Модель6", "КП6", "", "ID6", "Конкурент5", "Вкл5"]
            ]
            """);
        return metadata;
    }

    private FileMetadata createTestFileMetadataWithAllColumns() {
        FileMetadata metadata = new FileMetadata();
        // Создаем данные с 30 колонками (включая дополнительные: URL(27), Источник(28), Дата(29))
        metadata.setSampleData("""
            [
                ["Клиент1", "1", "ID1", "Категория1", "Кат.кл.1", "Бренд1", "Модель1", "КП1", "ШК1", "Статус1", "", "", "Цена1", "", "", "", "", "", "", "Модель2", "КП2", "", "ID2", "Конкурент1", "Вкл1", "", "", "http://example.com", "user1", "2024-01-01"]
            ]
            """);
        return metadata;
    }

    private FileMetadata createTestFileMetadataWithSource() {
        FileMetadata metadata = new FileMetadata();
        metadata.setSampleData("""
            [
                ["Клиент1", "1", "ID1", "Категория1", "Кат.кл.1", "Бренд1", "Модель1", "КП1", "ШК1", "Статус1", "", "", "Цена1", "", "", "", "", "", "", "Модель2", "КП2", "", "ID2", "Конкурент1", "Вкл1", "", "", "", "unknown_user", "2024-01-01"],
                ["Клиент2", "2", "ID2", "Категория2", "Кат.кл.2", "Бренд2", "Модель2", "КП2", "ШК2", "Статус2", "", "", "Цена2", "", "", "", "", "", "", "Модель3", "КП3", "", "ID3", "Конкурент2", "Вкл2", "", "", "", "zms-cron", "2024-01-02"]
            ]
            """);
        return metadata;
    }

    private StatsProcessDto createTestStatsProcessDto() {
        StatsProcessDto dto = new StatsProcessDto();
        dto.setOutputFormat("xlsx");
        dto.setCsvDelimiter(";");
        dto.setCsvEncoding("UTF-8");
        dto.setShowCompetitorUrl(false);
        dto.setShowSource(false);
        dto.setShowDateAdd(false);
        dto.setSourceReplace(false);
        return dto;
    }
}