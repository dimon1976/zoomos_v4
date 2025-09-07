package com.java.controller.utils;

import com.java.dto.FileAnalysisResultDto;
import com.java.dto.utils.RedirectFinderDto;
import com.java.model.entity.FileMetadata;
import com.java.service.file.FileAnalyzerService;
import com.java.service.utils.RedirectFinderService;
import com.java.util.ControllerUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Тесты для RedirectFinderController
 */
@WebMvcTest(RedirectFinderController.class)
class RedirectFinderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileAnalyzerService fileAnalyzerService;

    @MockBean
    private ControllerUtils controllerUtils;

    @MockBean
    private RedirectFinderService redirectFinderService;

    @Test
    void testIndex_ReturnsMainPage() throws Exception {
        // When & Then
        mockMvc.perform(get("/utils/redirect-finder"))
            .andExpect(status().isOk())
            .andExpect(view().name("utils/redirect-finder"))
            .andExpect(model().attribute("pageTitle", "Поиск финальных ссылок"))
            .andExpect(model().attributeExists("description"));
    }

    @Test
    void testUploadFile_WithValidFile_Success() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test.csv", 
            "text/csv", 
            "id,url\n1,https://example.com".getBytes()
        );

        FileAnalysisResultDto analysisResult = FileAnalysisResultDto.builder()
            .columnHeaders(List.of("id", "url"))
            .totalColumns(2)
            .totalRows(2)
            .build();

        when(fileAnalyzerService.analyzeFile(any(MockMultipartFile.class)))
            .thenReturn(FileMetadata.builder().build());

        // When & Then
        mockMvc.perform(multipart("/utils/redirect-finder/upload")
                .file(file)
                .sessionAttr("test", "session"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/utils/redirect-finder/configure"));
    }

    @Test
    void testUploadFile_WithInvalidFile_ReturnsError() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "empty.csv", 
            "text/csv", 
            "".getBytes()
        );

        when(fileAnalyzerService.analyzeFile(any(MockMultipartFile.class)))
            .thenThrow(new IllegalArgumentException("Файл пустой"));

        // When & Then
        mockMvc.perform(multipart("/utils/redirect-finder/upload")
                .file(file))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/utils/redirect-finder"))
            .andExpect(flash().attributeExists("error"));
    }

    @Test
    void testConfigure_WithFileInSession_ReturnsConfigPage() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        
        FileAnalysisResultDto analysisResult = FileAnalysisResultDto.builder()
            .columnHeaders(List.of("id", "url", "model"))
            .totalColumns(3)
            .totalRows(10)
            .build();
        
        session.setAttribute("fileAnalysisResult", analysisResult);
        session.setAttribute("fileMetadata", FileMetadata.builder().build());

        // When & Then
        mockMvc.perform(get("/utils/redirect-finder/configure")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(view().name("utils/redirect-finder-configure"))
            .andExpect(model().attributeExists("analysisResult"))
            .andExpect(model().attributeExists("dto"));
    }

    @Test
    void testConfigure_WithoutFileInSession_RedirectsToMain() throws Exception {
        // Given - пустая сессия
        MockHttpSession session = new MockHttpSession();

        // When & Then
        mockMvc.perform(get("/utils/redirect-finder/configure")
                .session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/utils/redirect-finder"))
            .andExpect(flash().attributeExists("error"));
    }

    @Test
    void testProcess_WithValidDto_ReturnsFile() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        FileMetadata fileMetadata = FileMetadata.builder()
            .originalFilename("test.csv")
            .build();
        session.setAttribute("fileMetadata", fileMetadata);

        byte[] resultFile = "id,url,final_url,redirects\n1,http://example.com,http://example.com,0".getBytes();
        when(redirectFinderService.processRedirectFinding(any(FileMetadata.class), any(RedirectFinderDto.class)))
            .thenReturn(resultFile);

        // When & Then
        mockMvc.perform(post("/utils/redirect-finder/process")
                .session(session)
                .param("urlColumn", "1")
                .param("idColumn", "0")
                .param("maxRedirects", "5")
                .param("timeoutMs", "5000")
                .param("outputFormat", "csv"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));

        // Verify service was called
        verify(redirectFinderService).processRedirectFinding(any(FileMetadata.class), any(RedirectFinderDto.class));
    }

    @Test 
    void testProcess_WithInvalidDto_ReturnsValidationError() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        FileMetadata fileMetadata = FileMetadata.builder().build();
        session.setAttribute("fileMetadata", fileMetadata);
        
        FileAnalysisResultDto analysisResult = FileAnalysisResultDto.builder()
            .columnHeaders(List.of("id", "url"))
            .build();
        session.setAttribute("fileAnalysisResult", analysisResult);

        // When & Then - отправляем невалидные данные (urlColumn = null)
        mockMvc.perform(post("/utils/redirect-finder/process")
                .session(session)
                .param("urlColumn", "") // Пустое значение
                .param("maxRedirects", "5"))
            .andExpect(status().isOk())
            .andExpect(view().name("utils/redirect-finder-configure"))
            .andExpect(model().hasErrors());
    }

    @Test
    void testProcess_WithoutFileInSession_RedirectsToMain() throws Exception {
        // Given - пустая сессия
        MockHttpSession session = new MockHttpSession();

        // When & Then
        mockMvc.perform(post("/utils/redirect-finder/process")
                .session(session)
                .param("urlColumn", "1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/utils/redirect-finder"))
            .andExpect(flash().attributeExists("error"));
    }

    @Test
    void testProcess_WithServiceException_ReturnsError() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        FileMetadata fileMetadata = FileMetadata.builder().build();
        session.setAttribute("fileMetadata", fileMetadata);
        
        FileAnalysisResultDto analysisResult = FileAnalysisResultDto.builder()
            .columnHeaders(List.of("id", "url"))
            .build();
        session.setAttribute("fileAnalysisResult", analysisResult);

        when(redirectFinderService.processRedirectFinding(any(FileMetadata.class), any(RedirectFinderDto.class)))
            .thenThrow(new RuntimeException("Ошибка обработки"));

        // When & Then
        mockMvc.perform(post("/utils/redirect-finder/process")
                .session(session)
                .param("urlColumn", "1")
                .param("maxRedirects", "5"))
            .andExpect(status().isOk())
            .andExpect(view().name("utils/redirect-finder-configure"))
            .andExpect(model().attributeExists("error"));
    }

    @Test
    void testCancel_ClearsSessionAndRedirects() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("fileMetadata", FileMetadata.builder().build());
        session.setAttribute("fileAnalysisResult", FileAnalysisResultDto.builder().build());

        // When & Then
        mockMvc.perform(post("/utils/redirect-finder/cancel")
                .session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/utils/redirect-finder"))
            .andExpect(flash().attribute("success", "Операция отменена"));

        // Session should be cleared (можем проверить через отдельный запрос)
    }

    @Test
    void testUploadFile_WithLargeFile_HandlesCorrectly() throws Exception {
        // Given
        StringBuilder largeContent = new StringBuilder("id,url\n");
        for (int i = 0; i < 1000; i++) {
            largeContent.append(i).append(",https://example").append(i).append(".com\n");
        }
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "large_test.csv", 
            "text/csv", 
            largeContent.toString().getBytes()
        );

        FileAnalysisResultDto analysisResult = FileAnalysisResultDto.builder()
            .columnHeaders(List.of("id", "url"))
            .totalColumns(2)
            .totalRows(1001)
            .build();

        when(fileAnalyzerService.analyzeFile(any(MockMultipartFile.class)))
            .thenReturn(FileMetadata.builder().build());

        // When & Then
        mockMvc.perform(multipart("/utils/redirect-finder/upload")
                .file(file))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/utils/redirect-finder/configure"));
    }

    @Test
    void testProcess_WithAllOptionalColumns_Success() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        FileMetadata fileMetadata = FileMetadata.builder()
            .originalFilename("test_full.csv")
            .build();
        session.setAttribute("fileMetadata", fileMetadata);

        byte[] resultFile = "id,model,url,final_url,redirects,status\n1,ProductA,http://example.com,http://example.com,0,OK".getBytes();
        when(redirectFinderService.processRedirectFinding(any(FileMetadata.class), any(RedirectFinderDto.class)))
            .thenReturn(resultFile);

        // When & Then - тест с идом, моделью и всеми опциями
        mockMvc.perform(post("/utils/redirect-finder/process")
                .session(session)
                .param("urlColumn", "2")    // URL колонка
                .param("idColumn", "0")     // ID колонка
                .param("modelColumn", "1")  // Model колонка
                .param("maxRedirects", "10")
                .param("timeoutMs", "8000")
                .param("outputFormat", "csv"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Content-Disposition"));

        // Verify all parameters were passed correctly
        verify(redirectFinderService).processRedirectFinding(
            any(FileMetadata.class), 
            argThat(dto -> 
                dto.getUrlColumn().equals(2) && 
                dto.getIdColumn().equals(0) && 
                dto.getModelColumn().equals(1) &&
                dto.getMaxRedirects().equals(10) &&
                dto.getTimeoutMs().equals(8000)
            )
        );
    }
}