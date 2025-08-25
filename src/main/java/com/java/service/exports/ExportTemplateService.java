package com.java.service.exports;

import com.java.dto.ExportTemplateDto;
import com.java.mapper.ExportTemplateMapper;
import com.java.model.Client;
import com.java.model.entity.ExportTemplate;
import com.java.model.enums.EntityType;
import com.java.repository.ClientRepository;
import com.java.repository.ExportSessionRepository;
import com.java.repository.ExportTemplateRepository;
import com.java.service.template.AbstractTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления шаблонами экспорта
 */
@Service
@Slf4j
public class ExportTemplateService extends AbstractTemplateService<ExportTemplate, ExportTemplateDto, ExportTemplateRepository> {

    private final ExportSessionRepository sessionRepository;

    public ExportTemplateService(ExportTemplateRepository templateRepository,
                               ClientRepository clientRepository,
                               ExportSessionRepository sessionRepository) {
        super(templateRepository, clientRepository);
        this.sessionRepository = sessionRepository;
    }

    // Реализация абстрактных методов AbstractTemplateService

    @Override
    protected Long getTemplateId(ExportTemplate template) {
        return template.getId();
    }

    @Override
    protected Long getTemplateClientId(ExportTemplate template) {
        return template.getClient().getId();
    }

    @Override
    protected String getTemplateName(ExportTemplateDto dto) {
        return dto.getName();
    }

    @Override
    protected Long getClientIdFromDto(ExportTemplateDto dto) {
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
        return sessionRepository.countByTemplateId(templateId) > 0;
    }

    @Override
    protected ExportTemplate createEntityFromDto(ExportTemplateDto dto, Client client) {
        return ExportTemplateMapper.toEntity(dto, client);
    }

    @Override
    protected ExportTemplate updateEntityFromDto(ExportTemplate existingTemplate, ExportTemplateDto dto, Client client) {
        ExportTemplateMapper.updateEntity(existingTemplate, dto);
        return existingTemplate;
    }

    @Override
    protected ExportTemplateDto createDtoFromEntity(ExportTemplate template) {
        return ExportTemplateMapper.toDto(template);
    }

    @Override
    protected ExportTemplate cloneEntity(ExportTemplate sourceTemplate, String newName, Client client) {
        // Создаем копию через DTO
        ExportTemplateDto dto = ExportTemplateMapper.toDto(sourceTemplate);
        dto.setId(null);
        dto.setName(newName);
        dto.setClientId(client.getId());
        dto.setClientName(null);
        dto.setCreatedAt(null);
        dto.setUpdatedAt(null);
        dto.setUsageCount(null);
        dto.setLastUsedAt(null);

        // Очищаем ID у полей и фильтров
        dto.getFields().forEach(field -> field.setId(null));
        dto.getFilters().forEach(filter -> filter.setId(null));

        return ExportTemplateMapper.toEntity(dto, client);
    }

    @Override
    protected Optional<ExportTemplate> findTemplateById(Long templateId) {
        return templateRepository.findByIdWithFieldsAndFilters(templateId);
    }

    @Override
    protected List<ExportTemplate> findTemplatesByClient(Client client) {
        return templateRepository.findByClientAndIsActiveTrue(client);
    }

    @Override
    protected Page<ExportTemplate> findTemplatesByClient(Client client, Pageable pageable) {
        return templateRepository.findByClientAndIsActiveTrue(client, pageable);
    }

    @Override
    protected ExportTemplate saveTemplate(ExportTemplate template) {
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

    // Дополнительные методы, специфичные для экспорта

    /**
     * Получает список шаблонов клиента с дополнительной статистикой
     */
    @Transactional(readOnly = true)
    public List<ExportTemplateDto> getClientTemplatesWithStats(Long clientId) {
        List<ExportTemplateDto> dtos = getClientTemplates(clientId);

        // Добавляем статистику использования
        for (ExportTemplateDto dto : dtos) {
            Long usageCount = sessionRepository.countByTemplateId(dto.getId());
            dto.setUsageCount(usageCount);

            // Получаем дату последнего использования
            sessionRepository.findLastByTemplateId(dto.getId())
                    .ifPresent(session -> dto.setLastUsedAt(session.getStartedAt()));
        }

        return dtos;
    }

    /**
     * Получает список шаблонов для определенного типа сущности
     */
    @Transactional(readOnly = true)
    public List<ExportTemplateDto> getTemplatesByEntityType(Long clientId, EntityType entityType) {
        Client client = getClientById(clientId);

        List<ExportTemplate> templates = templateRepository
                .findByClientAndEntityTypeAndIsActiveTrue(client, entityType);

        return ExportTemplateMapper.toDtoList(templates);
    }


    /**
     * Получает последний использованный шаблон клиента
     */
    @Transactional(readOnly = true)
    public Optional<ExportTemplateDto> getLastUsedTemplate(Long clientId) {
        return sessionRepository.findLastUsedTemplateByClientId(clientId)
                .map(ExportTemplateMapper::toDto);
    }
}