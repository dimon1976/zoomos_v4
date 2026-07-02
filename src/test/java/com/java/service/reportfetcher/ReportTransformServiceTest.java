package com.java.service.reportfetcher;

import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportOutputColumn;
import com.java.model.enums.ReportOutputColumnType;
import com.java.service.exports.FileGeneratorService;
import com.java.service.exports.formatter.ValueFormatter;
import com.java.service.exports.generator.CsvFileGenerator;
import com.java.service.exports.generator.XlsxFileGenerator;
import com.java.service.exports.style.ExcelStyleFactory;
import com.java.util.FileReaderUtils;
import com.java.util.PathResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportTransformServiceTest {

    private final FileReaderUtils fileReaderUtils = new FileReaderUtils();
    private final PathResolver pathResolver = createTestPathResolver();
    private final FileGeneratorService fileGeneratorService =
            new FileGeneratorService(List.of(
                    new CsvFileGenerator(pathResolver, new ValueFormatter()),
                    new XlsxFileGenerator(pathResolver, new ValueFormatter(), new ExcelStyleFactory())));
    private final ReportRowExpressionEvaluator evaluator = new ReportRowExpressionEvaluator();
    private final ReportTransformService transformService =
            new ReportTransformService(fileReaderUtils, fileGeneratorService, evaluator);

    private static PathResolver createTestPathResolver() {
        try {
            Path tempDir = Files.createTempDirectory("report-fetcher-test-temp-");
            Path exportDir = Files.createTempDirectory("report-fetcher-test-export-");
            PathResolver resolver = new PathResolver();
            ReflectionTestUtils.setField(resolver, "tempDir", tempDir.toString());
            ReflectionTestUtils.setField(resolver, "exportDir", exportDir.toString());
            return resolver;
        } catch (IOException e) {
            throw new RuntimeException("Failed to set up test PathResolver", e);
        }
    }

    private Path writeCsv(String content) throws IOException {
        Path file = Files.createTempFile("report-fetcher-test-", ".csv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private ReportConfig baseConfig() {
        return ReportConfig.builder()
                .id(1L)
                .name("Test Config")
                .outputFormat("CSV")
                .outputColumns(new ArrayList<>())
                .build();
    }

    @Test
    void shouldPassThroughSourceColumnsAndDetectHeaders() throws Exception {
        Path source = writeCsv("ОГРН;Цена;РРЦ\n111;150;100\n222;80;100\n");
        ReportConfig config = baseConfig();
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.SOURCE)
                .outputHeaderName("ОГРН").sourceColumnName("ОГРН").position(0).included(true).build());
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.SOURCE)
                .outputHeaderName("Цена").sourceColumnName("Цена").position(1).included(true).build());

        ReportTransformService.ReportTransformResult result =
                transformService.transform(source, "report.csv", "CSV", config);

        assertEquals(List.of("ОГРН", "Цена", "РРЦ"), result.reportHeaders());
        String resultContent = Files.readString(result.resultFilePath());
        assertTrue(resultContent.contains("111"));
        assertTrue(resultContent.contains("150"));
    }

    @Test
    void shouldComputeFormulaColumn() throws Exception {
        Path source = writeCsv("Цена;РРЦ\n150;100\n");
        ReportConfig config = baseConfig();
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.COMPUTED)
                .outputHeaderName("Разница").formula("['Цена'] - ['РРЦ']").position(0).included(true).build());

        ReportTransformService.ReportTransformResult result =
                transformService.transform(source, "report.csv", "CSV", config);

        String content = Files.readString(result.resultFilePath());
        assertTrue(content.contains("50"));
    }

    @Test
    void shouldApplyMultipleLookupColumnsFromSameKey() throws Exception {
        Path source = writeCsv("ОГРН;Цена\n111;150\n999;80\n");
        Path lookup = writeCsv("ОГРН;Юр. лицо;Город\n111;ООО Ромашка;Минск\n");

        ReportConfig config = baseConfig();
        config.setLookupFileStoredPath(lookup.toString());
        config.setLookupFileOriginalName("lookup.csv");
        config.setLookupFileFormat("CSV");
        config.setLookupFileDelimiter(";");
        config.setLookupFileEncoding("UTF-8");
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.LOOKUP)
                .outputHeaderName("Юр. лицо").keyColumnInReport("ОГРН").keyColumnInLookup("ОГРН")
                .valueColumnInLookup("Юр. лицо").position(0).included(true).build());
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.LOOKUP)
                .outputHeaderName("Город юр. лица").keyColumnInReport("ОГРН").keyColumnInLookup("ОГРН")
                .valueColumnInLookup("Город").position(1).included(true).build());

        ReportTransformService.ReportTransformResult result =
                transformService.transform(source, "report.csv", "CSV", config);

        String content = Files.readString(result.resultFilePath());
        assertTrue(content.contains("ООО Ромашка"));
        assertTrue(content.contains("Минск"));
    }

    @Test
    void shouldExcludeRowsFailingFilter() throws Exception {
        Path source = writeCsv("ОГРН;Цена\n111;150\n;80\n");
        ReportConfig config = baseConfig();
        config.setRowFilterExpression("['ОГРН'] != null and ['ОГРН'] != ''");
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.SOURCE)
                .outputHeaderName("ОГРН").sourceColumnName("ОГРН").position(0).included(true).build());

        ReportTransformService.ReportTransformResult result =
                transformService.transform(source, "report.csv", "CSV", config);

        String content = Files.readString(result.resultFilePath());
        assertTrue(content.contains("111"));
        assertFalse(content.contains("\n;")); // the row with empty ОГРН must be gone
    }

    @Test
    void shouldExcludeColumnsNotIncluded() throws Exception {
        Path source = writeCsv("ОГРН;Цена\n111;150\n");
        ReportConfig config = baseConfig();
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.SOURCE)
                .outputHeaderName("ОГРН").sourceColumnName("ОГРН").position(0).included(true).build());
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.SOURCE)
                .outputHeaderName("Цена").sourceColumnName("Цена").position(1).included(false).build());

        ReportTransformService.ReportTransformResult result =
                transformService.transform(source, "report.csv", "CSV", config);

        String content = Files.readString(result.resultFilePath());
        assertFalse(content.contains("Цена"));
    }

    @Test
    void shouldPreserveLeadingZerosForSourceColumnAndLookupKey() throws Exception {
        Path source = writeCsv("ОГРН;Цена\n0111;150\n");
        Path lookup = writeCsv("ОГРН;Юр. лицо\n0111;ООО Тест\n");

        ReportConfig config = baseConfig();
        config.setLookupFileStoredPath(lookup.toString());
        config.setLookupFileOriginalName("lookup.csv");
        config.setLookupFileFormat("CSV");
        config.setLookupFileDelimiter(";");
        config.setLookupFileEncoding("UTF-8");
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.SOURCE)
                .outputHeaderName("ОГРН").sourceColumnName("ОГРН").position(0).included(true).build());
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.LOOKUP)
                .outputHeaderName("Юр. лицо").keyColumnInReport("ОГРН").keyColumnInLookup("ОГРН")
                .valueColumnInLookup("Юр. лицо").position(1).included(true).build());

        ReportTransformService.ReportTransformResult result =
                transformService.transform(source, "report.csv", "CSV", config);

        String content = Files.readString(result.resultFilePath());
        assertTrue(content.contains("0111"), "Leading zero in ОГРН must be preserved in output, got: " + content);
        assertTrue(content.contains("ООО Тест"), "Lookup must match on the raw (uncoerced) key, got: " + content);
    }

    @Test
    void shouldPassThroughAllSourceColumnsWhenNoOutputColumnsConfigured() throws Exception {
        Path source = writeCsv("ОГРН;Цена;РРЦ\n111;150;100\n222;80;100\n");
        ReportConfig config = baseConfig(); // outputColumns intentionally left empty

        ReportTransformService.ReportTransformResult result =
                transformService.transform(source, "report.csv", "CSV", config);

        String content = Files.readString(result.resultFilePath());
        assertTrue(content.contains("ОГРН") && content.contains("Цена") && content.contains("РРЦ"),
                "All original headers must be present when no columns are configured, got: " + content);
        assertTrue(content.contains("111") && content.contains("150") && content.contains("100"),
                "All original data must be present when no columns are configured, got: " + content);
    }
}
