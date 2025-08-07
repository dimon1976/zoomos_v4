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
    public ImportTemplateDto cloneTemplate(Long templateId, String newName) {
        log.info("Клонирование шаблона ID: {} с новым именем: {}", templateId, newName);

        ImportTemplate original = templateRepository.findByIdWithFields(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

        // Проверяем уникальность нового имени
        if (templateRepository.existsByNameAndClient(newName, original.getClient())) {
            throw new IllegalArgumentException("Шаблон с таким именем уже существует");
        }

        // Создаем копию
        ImportTemplate clone = ImportTemplate.builder()
                .name(newName)
                .description("Копия " + original.getDescription())
                .client(original.getClient())
                .entityType(original.getEntityType())
                .dataSourceType(original.getDataSourceType())
                .duplicateStrategy(original.getDuplicateStrategy())
                .errorStrategy(original.getErrorStrategy())
                .fileType(original.getFileType())
                .delimiter(original.getDelimiter())
                .encoding(original.getEncoding())
                .skipHeaderRows(original.getSkipHeaderRows())
                .isActive(true)
                .build();

        // Копируем поля
        for (ImportTemplateField originalField : original.getFields()) {
            ImportTemplateField clonedField = ImportTemplateField.builder()
                    .columnName(originalField.getColumnName())
                    .columnIndex(originalField.getColumnIndex())
                    .entityFieldName(originalField.getEntityFieldName())
                    .fieldType(originalField.getFieldType())
                    .isRequired(originalField.getIsRequired())
                    .isUnique(originalField.getIsUnique())
                    .defaultValue(originalField.getDefaultValue())
                    .dateFormat(originalField.getDateFormat())
                    .transformationRule(originalField.getTransformationRule())
                    .validationRegex(originalField.getValidationRegex())
                    .validationMessage(originalField.getValidationMessage())
                    .build();
            clone.addField(clonedField);
        }

        clone = templateRepository.save(clone);
        log.info("Шаблон клонирован с новым ID: {}", clone.getId());

        return ImportTemplateMapper.toDto(clone);
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

        // Очищаем старые поля
        template.getFields().clear();

        // Добавляем новые
        for (ImportTemplateFieldDto fieldDto : fields) {
            ImportTemplateField field = ImportTemplateMapper.fieldToEntity(fieldDto);
            template.addField(field);
        }

        // Валидируем поля
        validationService.validateTemplateFields(fields, template.getEntityType());

        template = templateRepository.save(template);
        log.info("Поля шаблона обновлены");

        return ImportTemplateMapper.toDto(template);
    }
}