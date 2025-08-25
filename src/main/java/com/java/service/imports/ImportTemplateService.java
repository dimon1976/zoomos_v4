package com.java.service.imports;

import com.java.dto.ImportTemplateDto;
import com.java.dto.ImportTemplateFieldDto;
import com.java.mapper.ImportTemplateMapper;
import com.java.model.Client;
import com.java.model.entity.ImportTemplate;
import com.java.model.entity.ImportTemplateField;
import com.java.repository.ClientRepository;
import com.java.repository.ImportTemplateRepository;
import com.java.service.imports.validation.TemplateValidationService;
import com.java.service.template.AbstractTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления шаблонами импорта
 */
@Service
@Slf4j
public class ImportTemplateService extends AbstractTemplateService<ImportTemplate, ImportTemplateDto, ImportTemplateRepository> {

    private final TemplateValidationService validationService;

    public ImportTemplateService(ImportTemplateRepository templateRepository,
                               ClientRepository clientRepository,
                               TemplateValidationService validationService) {
        super(templateRepository, clientRepository);
        this.validationService = validationService;
    }

    @Override
    protected void validateTemplate(ImportTemplateDto dto) {
        validationService.validateTemplate(dto);
    }

    // Реализация абстрактных методов AbstractTemplateService

    @Override
    protected Long getTemplateId(ImportTemplate template) {
        return template.getId();
    }

    @Override
    protected Long getTemplateClientId(ImportTemplate template) {
        return template.getClient().getId();
    }

    @Override
    protected String getTemplateName(ImportTemplateDto dto) {
        return dto.getName();
    }

    @Override
    protected Long getClientIdFromDto(ImportTemplateDto dto) {
        return dto.getClientId();
    }

    @Override
    protected boolean isTemplateNameExists(String templateName, Client client, Long excludeTemplateId) {
        if (excludeTemplateId != null) {
            return templateRepository.existsByNameAndClientAndIdNot(templateName, client, excludeTemplateId);
        }
        return templateRepository.existsByNameAndClient(templateName, client);
    }

    @Override
    protected boolean isTemplateInUse(Long templateId) {
        // Простая проверка - можно удалять все шаблоны импорта
        return false;
    }

    @Override
    protected ImportTemplate createEntityFromDto(ImportTemplateDto dto, Client client) {
        return ImportTemplateMapper.toEntity(dto, client);
    }

    @Override
    protected ImportTemplate updateEntityFromDto(ImportTemplate existingTemplate, ImportTemplateDto dto, Client client) {
        ImportTemplateMapper.updateEntity(existingTemplate, dto);
        return existingTemplate;
    }

    @Override
    protected ImportTemplateDto createDtoFromEntity(ImportTemplate template) {
        return ImportTemplateMapper.toDto(template);
    }

    @Override
    protected ImportTemplate cloneEntity(ImportTemplate sourceTemplate, String newName, Client client) {
        // Создаем копию через DTO
        ImportTemplateDto dto = ImportTemplateMapper.toDto(sourceTemplate);
        dto.setId(null);
        dto.setName(newName);
        dto.setClientId(client.getId());
        dto.setClientName(null);
        dto.setCreatedAt(null);
        dto.setUpdatedAt(null);
        dto.setUsageCount(null);
        dto.setLastUsedAt(null);

        // Очищаем ID у полей
        dto.getFields().forEach(field -> field.setId(null));

        return ImportTemplateMapper.toEntity(dto, client);
    }

    @Override
    protected Optional<ImportTemplate> findTemplateById(Long templateId) {
        return templateRepository.findByIdWithFields(templateId);
    }

    @Override
    protected List<ImportTemplate> findTemplatesByClient(Client client) {
        return templateRepository.findByClientAndIsActiveTrue(client);
    }

    @Override
    protected Page<ImportTemplate> findTemplatesByClient(Client client, Pageable pageable) {
        return templateRepository.findByClientAndIsActiveTrue(client, pageable);
    }

    @Override
    protected ImportTemplate saveTemplate(ImportTemplate template) {
        return templateRepository.save(template);
    }

    @Override
    protected boolean existsById(Long templateId) {
        return templateRepository.existsById(templateId);
    }

    @Override
    protected void deleteById(Long templateId) {
        templateRepository.deleteById(templateId);
    }

    // Дополнительные методы, специфичные для импорта

    /**
     * Получает шаблон с проверкой доступа клиента
     */
    @Transactional(readOnly = true)
    public Optional<ImportTemplateDto> getTemplateForClient(Long templateId, Long clientId) {
        return templateRepository.findByIdWithFields(templateId)
                .filter(t -> t.getClient().getId().equals(clientId))
                .map(ImportTemplateMapper::toDto);
    }

    /**
     * Получает активные шаблоны с пагинацией
     */
    @Transactional(readOnly = true)
    public Page<ImportTemplateDto> getActiveTemplates(Pageable pageable) {
        return templateRepository.findAll(pageable)
                .map(ImportTemplateMapper::toDto);
    }

    /**
     * Деактивирует шаблон (мягкое удаление)
     */
    @Transactional
    public void deactivateTemplate(Long templateId) {
        log.info("Деактивация шаблона ID: {}", templateId);

        ImportTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

        template.setIsActive(false);
        templateRepository.save(template);

        log.info("Шаблон деактивирован");
    }

    /**
     * Получает статистику использования шаблонов клиента
     */
    @Transactional(readOnly = true)
    public List<Object[]> getTemplateUsageStatistics(Long clientId) {
        Client client = getClientById(clientId);
        return templateRepository.findTemplatesWithUsageCount(client);
    }

    /**
     * Обновляет маппинг полей шаблона
     */
    @Transactional
    public ImportTemplateDto updateTemplateFields(Long templateId, List<ImportTemplateFieldDto> fields) {
        log.info("Обновление полей шаблона ID: {}", templateId);

        ImportTemplate template = templateRepository.findByIdWithFields(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

        // Валидируем поля (метод может удалить пустые элементы из списка)
        validationService.validateTemplateFields(fields, template.getEntityType());

        // Очищаем старые поля и добавляем проверенные
        template.getFields().clear();
        for (ImportTemplateFieldDto fieldDto : fields) {
            ImportTemplateField field = ImportTemplateMapper.fieldToEntity(fieldDto);
            template.addField(field);
        }

        template = templateRepository.save(template);
        log.info("Поля шаблона обновлены");

        return ImportTemplateMapper.toDto(template);
    }
}