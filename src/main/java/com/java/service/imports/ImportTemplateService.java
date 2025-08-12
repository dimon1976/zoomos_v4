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
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ImportTemplateService {

    private final ImportTemplateRepository templateRepository;
    private final ClientRepository clientRepository;
    private final TemplateValidationService validationService;

    /**
     * Создает новый шаблон импорта
     */
    @Transactional
    public ImportTemplateDto createTemplate(ImportTemplateDto dto) {
        log.info("Создание шаблона импорта: {}", dto.getName());

        // Получаем клиента
        Client client = clientRepository.findById(dto.getClientId())
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));

        // Проверяем уникальность имени шаблона для клиента
        if (templateRepository.existsByNameAndClient(dto.getName(), client)) {
            throw new IllegalArgumentException("Шаблон с таким именем уже существует");
        }

        // Валидируем шаблон
        validationService.validateTemplate(dto);

        // Создаем entity
        ImportTemplate template = ImportTemplateMapper.toEntity(dto, client);

        // Сохраняем
        template = templateRepository.save(template);
        log.info("Шаблон импорта создан с ID: {}", template.getId());

        return ImportTemplateMapper.toDto(template);
    }

    /**
     * Обновляет существующий шаблон
     */
    @Transactional
    public ImportTemplateDto updateTemplate(Long templateId, ImportTemplateDto dto) {
        log.info("Обновление шаблона импорта ID: {}", templateId);

        ImportTemplate template = templateRepository.findByIdWithFields(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

        // Проверяем принадлежность клиенту
        if (!template.getClient().getId().equals(dto.getClientId())) {
            throw new IllegalArgumentException("Нельзя изменить клиента шаблона");
        }

        // Проверяем уникальность имени если оно изменилось
        if (!template.getName().equals(dto.getName()) &&
                templateRepository.existsByNameAndClient(dto.getName(), template.getClient())) {
            throw new IllegalArgumentException("Шаблон с таким именем уже существует");
        }

        // Валидируем
        validationService.validateTemplate(dto);

        // Обновляем
        ImportTemplateMapper.updateEntity(template, dto);
        template = templateRepository.save(template);

        log.info("Шаблон импорта обновлен");
        return ImportTemplateMapper.toDto(template);
    }

    /**
     * Получает шаблон по ID
     */
    @Transactional(readOnly = true)
    public Optional<ImportTemplateDto> getTemplate(Long templateId) {
        return templateRepository.findByIdWithFields(templateId)
                .map(ImportTemplateMapper::toDto);
    }

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
     * Получает список шаблонов клиента
     */
    @Transactional(readOnly = true)
    public List<ImportTemplateDto> getClientTemplates(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));

        List<ImportTemplate> templates = templateRepository.findByClientAndIsActiveTrue(client);
        return ImportTemplateMapper.toDtoList(templates);
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
     * Удаляет шаблон полностью
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
     * Клонирует существующий шаблон
     */
    @Transactional
    public ImportTemplateDto cloneTemplate(Long templateId, String newName, Long clientId) {
        log.info("Клонирование шаблона ID: {} с новым именем: {} для клиента {}", templateId, newName, clientId);

        ImportTemplate original = templateRepository.findByIdWithFields(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

        Long targetClientId = clientId != null ? clientId : original.getClient().getId();
        Client targetClient = clientRepository.findById(targetClientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));

        // Проверяем уникальность нового имени
        if (templateRepository.existsByNameAndClient(newName, targetClient)) {
            throw new IllegalArgumentException("Шаблон с таким именем уже существует");
        }

        ImportTemplateDto dto = ImportTemplateMapper.toDto(original);
        dto.setId(null);
        dto.setName(newName);
        dto.setClientId(targetClientId);
        dto.setClientName(null);
        dto.setCreatedAt(null);
        dto.setUpdatedAt(null);
        dto.setIsActive(true);
        if (dto.getFields() != null) {
            dto.getFields().forEach(field -> field.setId(null));
        }

        return createTemplate(dto);
    }

    /**
     * Получает статистику использования шаблонов клиента
     */
    @Transactional(readOnly = true)
    public List<Object[]> getTemplateUsageStatistics(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));

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