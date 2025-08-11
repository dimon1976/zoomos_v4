package com.java.controller;

import com.java.dto.ExportTemplateDto;
import com.java.dto.ImportTemplateDto;
import com.java.dto.TemplateSummaryDto;
import com.java.mapper.TemplateMapper;
import com.java.service.exports.ExportTemplateService;
import com.java.service.imports.ImportTemplateService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST контроллер для получения короткой информации о шаблонах импорта и экспорта
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class TemplateRestController {

    private final ImportTemplateService importTemplateService;
    private final ExportTemplateService exportTemplateService;

    /**
     * Получение короткого списка шаблонов импорта клиента
     */
    @GetMapping("/import/templates/client/{clientId}/summary")
    public ResponseEntity<List<TemplateSummaryDto>> getImportTemplates(@PathVariable @Positive Long clientId) {
        log.debug("REST запрос на получение шаблонов импорта для клиента {}", clientId);
        try {
            List<ImportTemplateDto> templates = importTemplateService.getClientTemplates(clientId);
            List<TemplateSummaryDto> result = templates.stream()
                    .map(TemplateMapper::toSummary)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка получения шаблонов импорта для клиента {}", clientId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось получить шаблоны импорта", e);
        }
    }

    /**
     * Получение короткого списка шаблонов экспорта клиента
     */
    @GetMapping("/export/templates/client/{clientId}/summary")
    public ResponseEntity<List<TemplateSummaryDto>> getExportTemplates(@PathVariable @Positive Long clientId) {
        log.debug("REST запрос на получение шаблонов экспорта для клиента {}", clientId);
        try {
            List<ExportTemplateDto> templates = exportTemplateService.getClientTemplates(clientId);
            List<TemplateSummaryDto> result = templates.stream()
                    .map(TemplateMapper::toSummary)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка получения шаблонов экспорта для клиента {}", clientId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось получить шаблоны экспорта", e);
        }
    }
}