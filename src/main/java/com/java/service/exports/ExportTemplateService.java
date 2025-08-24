package com.java.service.exports;

import com.java.dto.ExportTemplateDto;
import com.java.mapper.ExportTemplateMapper;
import com.java.model.Client;
import com.java.model.entity.ExportTemplate;
import com.java.model.enums.EntityType;
import com.java.repository.ClientRepository;
import com.java.repository.ExportSessionRepository;
import com.java.repository.ExportTemplateRepository;
import com.java.util.TemplateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления шаблонами экспорта
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExportTemplateService {

    private final ExportTemplateRepository templateRepository;
    private final ExportSessionRepository sessionRepository;
    private final ClientRepository clientRepository;
    private final TemplateUtils templateUtils;

    /**
     * Создает новый шаблон экспорта
     */
    @Transactional
    public ExportTemplateDto createTemplate(ExportTemplateDto dto) {
        log.info("Создание шаблона экспорта: {}", dto.getName());

        Client client = templateUtils.getClientById(dto.getClientId());
        templateUtils.validateTemplateNameUniqueness(dto.getName(), client,
                templateRepository.existsByNameAndClient(dto.getName(), client));

        // Создаем entity
        ExportTemplate template = ExportTemplateMapper.toEntity(dto, client);

        // Сохраняем
        template = templateRepository.save(template);
        log.info("Шаблон экспорта создан с ID: {}", template.getId());

        return ExportTemplateMapper.toDto(template);
    }

    /**
     * Обновляет существующий шаблон
     */
    @Transactional
    public ExportTemplateDto updateTemplate(Long templateId, ExportTemplateDto dto) {
        log.info("Обновление шаблона экспорта ID: {}", templateId);

        ExportTemplate template = templateRepository.findByIdWithFieldsAndFilters(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

        templateUtils.validateTemplateOwnership(template.getClient().getId(), dto.getClientId());
        
        if (!template.getName().equals(dto.getName())) {
            templateUtils.validateTemplateNameUniqueness(dto.getName(), template.getClient(),
                    templateRepository.existsByNameAndClient(dto.getName(), template.getClient()));
        }

        // Обновляем
        ExportTemplateMapper.updateEntity(template, dto);
        template = templateRepository.save(template);

        log.info("Шаблон экспорта обновлен");
        return ExportTemplateMapper.toDto(template);
    }

    /**
     * Получает шаблон по ID
     */
    @Transactional(readOnly = true)
    public Optional<ExportTemplateDto> getTemplate(Long templateId) {
        return templateRepository.findByIdWithFieldsAndFilters(templateId)
                .map(ExportTemplateMapper::toDto);
    }

    /**
     * Получает список шаблонов клиента
     */
    @Transactional(readOnly = true)
    public List<ExportTemplateDto> getClientTemplates(Long clientId) {
        Client client = templateUtils.getClientById(clientId);

        List<ExportTemplate> templates = templateRepository.findByClientAndIsActiveTrue(client);

        // Добавляем статистику использования
        List<ExportTemplateDto> dtos = ExportTemplateMapper.toDtoList(templates);

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
        Client client = templateUtils.getClientById(clientId);

        List<ExportTemplate> templates = templateRepository
                .findByClientAndEntityTypeAndIsActiveTrue(client, entityType);

        return ExportTemplateMapper.toDtoList(templates);
    }

    /**
     * Удаляет шаблон
     */
    @Transactional
    public void deleteTemplate(Long templateId) {
        log.info("Удаление шаблона ID: {}", templateId);

        if (!templateRepository.existsById(templateId)) {
            throw new IllegalArgumentException("Шаблон не найден");
        }

        templateRepository.deleteById(templateId);
        log.info("Шаблон удален");
    }

    /**
     * Клонирует шаблон
     */
    @Transactional
    public ExportTemplateDto cloneTemplate(Long templateId, String newName, Long clientId) {
        log.info("Клонирование шаблона ID: {} с новым именем: {} для клиента {}", templateId, newName, clientId);

        ExportTemplate original = templateRepository.findByIdWithFieldsAndFilters(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

        Long targetClientId = clientId != null ? clientId : original.getClient().getId();
        Client targetClient = templateUtils.getClientById(targetClientId);
        templateUtils.validateTemplateNameUniqueness(newName, targetClient,
                templateRepository.existsByNameAndClient(newName, targetClient));

        // Создаем копию через DTO
        ExportTemplateDto dto = ExportTemplateMapper.toDto(original);
        dto.setId(null);
        dto.setName(newName);
        dto.setClientId(targetClientId);
        dto.setClientName(null);
        dto.setCreatedAt(null);
        dto.setUpdatedAt(null);
        dto.setUsageCount(null);
        dto.setLastUsedAt(null);

        // Очищаем ID у полей и фильтров
        dto.getFields().forEach(field -> field.setId(null));
        dto.getFilters().forEach(filter -> filter.setId(null));

        return createTemplate(dto);
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