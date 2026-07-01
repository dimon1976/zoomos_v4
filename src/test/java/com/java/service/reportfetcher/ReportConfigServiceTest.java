package com.java.service.reportfetcher;

import com.java.dto.reportfetcher.ReportConfigDto;
import com.java.dto.reportfetcher.ReportOutputColumnDto;
import com.java.model.entity.ReportConfig;
import com.java.model.enums.ReportOutputColumnType;
import com.java.repository.ClientRepository;
import com.java.repository.ReportConfigRepository;
import com.java.util.FileReaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportConfigServiceTest {

    private ReportConfigRepository reportConfigRepository;
    private ReportConfigService service;

    @BeforeEach
    void setUp() {
        reportConfigRepository = mock(ReportConfigRepository.class);
        ClientRepository clientRepository = mock(ClientRepository.class);
        FileReaderUtils fileReaderUtils = new FileReaderUtils();
        service = new ReportConfigService(reportConfigRepository, clientRepository, fileReaderUtils);

        when(reportConfigRepository.save(any(ReportConfig.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ReportConfigDto dtoWithColumns(ReportOutputColumnDto... columns) {
        ReportConfigDto dto = new ReportConfigDto();
        dto.setName("Test config");
        dto.setSourceUrl("https://export.zoomos.by/shop/test/export");
        dto.setOutputFormat("XLSX");
        dto.setOutputColumns(List.of(columns));
        return dto;
    }

    private ReportOutputColumnDto lookupColumn(String outputName, String keyColumnInLookup) {
        ReportOutputColumnDto column = new ReportOutputColumnDto();
        column.setType(ReportOutputColumnType.LOOKUP);
        column.setOutputHeaderName(outputName);
        column.setIncluded(true);
        column.setKeyColumnInReport("ОГРН");
        column.setKeyColumnInLookup(keyColumnInLookup);
        column.setValueColumnInLookup(outputName);
        return column;
    }

    @Test
    void shouldSaveNewConfigWithOutputColumns() {
        ReportOutputColumnDto sourceColumn = new ReportOutputColumnDto();
        sourceColumn.setType(ReportOutputColumnType.SOURCE);
        sourceColumn.setOutputHeaderName("Цена");
        sourceColumn.setIncluded(true);
        sourceColumn.setSourceColumnName("Цена");

        ReportConfig saved = service.save(dtoWithColumns(sourceColumn));

        assertEquals("Test config", saved.getName());
        assertEquals(1, saved.getOutputColumns().size());
        assertEquals(0, saved.getOutputColumns().get(0).getPosition());
    }

    @Test
    void shouldRejectMixedLookupKeyColumns() {
        ReportConfigDto dto = dtoWithColumns(
                lookupColumn("Юр. лицо", "ОГРН"),
                lookupColumn("Город", "ИНН") // different key column in the lookup file — not allowed
        );

        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
    }

    @Test
    void shouldAllowMultipleLookupColumnsWithSameKey() {
        ReportConfig saved = service.save(dtoWithColumns(
                lookupColumn("Юр. лицо", "ОГРН"),
                lookupColumn("Город", "ОГРН")
        ));

        assertEquals(2, saved.getOutputColumns().size());
    }

    @Test
    void shouldThrowWhenUpdatingMissingConfig() {
        when(reportConfigRepository.findById(42L)).thenReturn(Optional.empty());
        ReportConfigDto dto = dtoWithColumns();
        dto.setId(42L);

        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
    }

    @Test
    void shouldSanitizeFilenameToPreventPathTraversal() throws Exception {
        Path lookupDir = Files.createTempDirectory("report-fetcher-lookup-test-");
        ReflectionTestUtils.setField(service, "lookupFileDir", lookupDir.toString());

        when(reportConfigRepository.findById(1L))
                .thenReturn(Optional.of(ReportConfig.builder().id(1L).name("Test").build()));

        MockMultipartFile maliciousFile = new MockMultipartFile(
                "file", "../../../../evil.csv", "text/csv", "a,b\n1,2".getBytes());

        service.attachLookupFile(1L, maliciousFile);

        ArgumentCaptor<ReportConfig> captor = ArgumentCaptor.forClass(ReportConfig.class);
        verify(reportConfigRepository).save(captor.capture());
        ReportConfig saved = captor.getValue();

        Path storedPath = Path.of(saved.getLookupFileStoredPath());
        assertTrue(storedPath.normalize().startsWith(lookupDir.normalize()),
                "Stored file must stay inside the configured lookup directory, got: " + storedPath);
        assertTrue(Files.exists(storedPath));
    }
}
