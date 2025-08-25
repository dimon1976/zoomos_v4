package com.java.service.operations;

import com.java.model.Client;
import com.java.model.FileOperation;
import com.java.model.entity.ImportSession;
import com.java.model.entity.ImportTemplate;
import com.java.model.enums.EntityType;
import com.java.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тест для сервиса удаления операций
 */
@ExtendWith(MockitoExtension.class)
@Transactional
class OperationDeletionServiceTest {

    @Mock
    private FileOperationRepository fileOperationRepository;
    
    @Mock
    private ImportSessionRepository importSessionRepository;
    
    @Mock
    private ExportSessionRepository exportSessionRepository;
    
    @Mock
    private ImportErrorRepository importErrorRepository;
    
    @Mock
    private FileMetadataRepository fileMetadataRepository;
    
    @Mock
    private ExportStatisticsRepository exportStatisticsRepository;
    
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private OperationDeletionService operationDeletionService;

    private FileOperation testOperation;
    private ImportSession testImportSession;
    private Client testClient;
    private ImportTemplate testTemplate;

    @BeforeEach
    void setUp() {
        testClient = new Client();
        testClient.setId(1L);
        testClient.setName("Test Client");

        testOperation = new FileOperation();
        testOperation.setId(100L);
        testOperation.setClient(testClient);
        testOperation.setOperationType(FileOperation.OperationType.IMPORT);
        testOperation.setStatus(FileOperation.OperationStatus.COMPLETED);
        testOperation.setFileName("test.csv");
        testOperation.setStartedAt(ZonedDateTime.now());

        testTemplate = new ImportTemplate();
        testTemplate.setId(10L);
        testTemplate.setName("Test Template");
        testTemplate.setEntityType(EntityType.AV_DATA);

        testImportSession = new ImportSession();
        testImportSession.setId(50L);
        testImportSession.setFileOperation(testOperation);
        testImportSession.setTemplate(testTemplate);
    }

    @Test
    void shouldThrowExceptionWhenOperationNotFound() {
        // Given
        Long nonExistentOperationId = 999L;
        when(fileOperationRepository.existsById(nonExistentOperationId)).thenReturn(false);

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> operationDeletionService.deleteOperationCompletely(nonExistentOperationId)
        );

        assertEquals("Операция не найдена", exception.getMessage());
        verify(fileOperationRepository).existsById(nonExistentOperationId);
        verifyNoMoreInteractions(importSessionRepository, exportSessionRepository);
    }

    @Test
    void shouldDeleteOperationWithImportData() {
        // Given
        Long operationId = testOperation.getId();
        Long sessionId = testImportSession.getId();
        
        when(fileOperationRepository.existsById(operationId)).thenReturn(true);
        when(importSessionRepository.findByFileOperationId(operationId)).thenReturn(Optional.of(testImportSession));
        when(exportSessionRepository.findByFileOperationId(operationId)).thenReturn(Optional.empty());
        
        // Mock JDBC template calls
        when(jdbcTemplate.update("DELETE FROM av_data WHERE operation_id = ?", operationId)).thenReturn(5);
        when(jdbcTemplate.update("DELETE FROM av_handbook WHERE import_session_id = ?", sessionId)).thenReturn(3);

        // When
        assertDoesNotThrow(() -> operationDeletionService.deleteOperationCompletely(operationId));

        // Then
        verify(fileOperationRepository).existsById(operationId);
        verify(importSessionRepository).findByFileOperationId(operationId);
        verify(exportSessionRepository).findByFileOperationId(operationId);
        
        // Verify data deletion
        verify(jdbcTemplate).update("DELETE FROM av_data WHERE operation_id = ?", operationId);
        verify(jdbcTemplate).update("DELETE FROM av_handbook WHERE import_session_id = ?", sessionId);
        
        // Verify cascade deletion
        verify(importErrorRepository).deleteByImportSessionId(sessionId);
        verify(fileMetadataRepository).findByImportSessionId(sessionId);
        verify(importSessionRepository).delete(testImportSession);
        verify(fileOperationRepository).deleteById(operationId);
    }

    @Test
    void shouldCalculateDeletionStatistics() {
        // Given
        Long operationId = testOperation.getId();
        Long sessionId = testImportSession.getId();
        
        when(importSessionRepository.findByFileOperationId(operationId)).thenReturn(Optional.of(testImportSession));
        when(exportSessionRepository.findByFileOperationId(operationId)).thenReturn(Optional.empty());
        
        // Mock counts
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM av_data WHERE operation_id = ?", Integer.class, operationId))
            .thenReturn(10);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM av_handbook WHERE import_session_id = ?", Integer.class, sessionId))
            .thenReturn(5);
        when(importErrorRepository.countByImportSessionId(sessionId)).thenReturn(2L);

        // When
        OperationDeletionService.DeletionStatistics stats = operationDeletionService.getDeletionStatistics(operationId);

        // Then
        assertNotNull(stats);
        assertEquals(operationId, stats.getOperationId());
        assertEquals(10, stats.getAvDataRecords());
        assertEquals(5, stats.getAvHandbookRecords());
        assertEquals(2L, stats.getImportErrors());
        assertEquals(0, stats.getExportStatistics());
        assertTrue(stats.isHasImportSession());
        assertFalse(stats.isHasExportSession());
        assertEquals(17, stats.getTotalRecords()); // 10 + 5 + 2 + 0
    }

    @Test
    void shouldHandleOperationWithoutSessions() {
        // Given
        Long operationId = testOperation.getId();
        
        when(fileOperationRepository.existsById(operationId)).thenReturn(true);
        when(importSessionRepository.findByFileOperationId(operationId)).thenReturn(Optional.empty());
        when(exportSessionRepository.findByFileOperationId(operationId)).thenReturn(Optional.empty());

        // When
        assertDoesNotThrow(() -> operationDeletionService.deleteOperationCompletely(operationId));

        // Then
        verify(fileOperationRepository).existsById(operationId);
        verify(importSessionRepository).findByFileOperationId(operationId);
        verify(exportSessionRepository).findByFileOperationId(operationId);
        verify(fileOperationRepository).deleteById(operationId);
        
        // Should not interact with other repositories
        verifyNoInteractions(jdbcTemplate, importErrorRepository, fileMetadataRepository, exportStatisticsRepository);
    }
}