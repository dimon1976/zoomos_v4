package com.java.service.template;

import com.java.model.Client;
import com.java.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Абстрактный базовый сервис для работы с шаблонами
 * @param <T> Тип сущности шаблона (ImportTemplate или ExportTemplate)
 * @param <D> Тип DTO шаблона (ImportTemplateDto или ExportTemplateDto)
 * @param <R> Тип репозитория шаблона
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractTemplateService<T, D, R> {

    protected final R templateRepository;
    protected final ClientRepository clientRepository;

    /**
     * Создает новый шаблон
     */
    @Transactional
    public D createTemplate(D dto) {
        log.info("Создание шаблона: {}", getTemplateName(dto));

        Client client = getClientById(getClientIdFromDto(dto));
        validateUniqueTemplateName(getTemplateName(dto), client, null);

        // Дополнительная валидация (если нужна для конкретного типа)
        validateTemplate(dto);

        // Создаем entity
        T template = createEntityFromDto(dto, client);

        // Сохраняем
        template = saveTemplate(template);
        log.info("Шаблон создан с ID: {}", getTemplateId(template));

        return createDtoFromEntity(template);
    }

    /**
     * Обновляет существующий шаблон
     */
    @Transactional
    public D updateTemplate(Long templateId, D dto) {
        log.info("Обновление шаблона ID: {}", templateId);

        T existingTemplate = getTemplateById(templateId);
        Client client = getClientById(getClientIdFromDto(dto));
        
        validateTemplateOwnership(existingTemplate, getClientIdFromDto(dto));
        validateUniqueTemplateName(getTemplateName(dto), client, templateId);

        // Дополнительная валидация
        validateTemplate(dto);

        // Обновляем entity
        T updatedTemplate = updateEntityFromDto(existingTemplate, dto, client);

        // Сохраняем
        updatedTemplate = saveTemplate(updatedTemplate);
        log.info("Шаблон обновлен ID: {}", templateId);

        return createDtoFromEntity(updatedTemplate);
    }

    /**
     * Получает шаблон по ID
     */
    @Transactional(readOnly = true)
    public Optional<D> getTemplate(Long templateId) {
        return findTemplateById(templateId)
                .map(this::createDtoFromEntity);
    }

    /**
     * Удаляет шаблон
     */
    @Transactional
    public void deleteTemplate(Long templateId) {
        log.info("Удаление шаблона ID: {}", templateId);

        if (!existsById(templateId)) {
            throw new IllegalArgumentException("Шаблон не найден");
        }

        // Проверяем, не используется ли шаблон
        if (isTemplateInUse(templateId)) {
            throw new IllegalArgumentException("Нельзя удалить шаблон, который используется в операциях");
        }

        deleteById(templateId);
        log.info("Шаблон удален ID: {}", templateId);
    }

    /**
     * Клонирует шаблон
     */
    @Transactional
    public D cloneTemplate(Long templateId, String newName, Long clientId) {
        log.info("Клонирование шаблона ID: {} с новым именем: {}", templateId, newName);

        T sourceTemplate = getTemplateById(templateId);
        Client client = getClientById(clientId);
        validateUniqueTemplateName(newName, client, null);

        T clonedTemplate = cloneEntity(sourceTemplate, newName, client);
        clonedTemplate = saveTemplate(clonedTemplate);

        log.info("Шаблон клонирован с ID: {}", getTemplateId(clonedTemplate));
        return createDtoFromEntity(clonedTemplate);
    }

    /**
     * Получает шаблоны клиента
     */
    @Transactional(readOnly = true)
    public List<D> getClientTemplates(Long clientId) {
        Client client = getClientById(clientId);
        return findTemplatesByClient(client).stream()
                .map(this::createDtoFromEntity)
                .toList();
    }

    /**
     * Получает шаблоны клиента с пагинацией
     */
    @Transactional(readOnly = true)
    public Page<D> getClientTemplates(Long clientId, Pageable pageable) {
        Client client = getClientById(clientId);
        return findTemplatesByClient(client, pageable)
                .map(this::createDtoFromEntity);
    }

    // Общие вспомогательные методы

    @Transactional(readOnly = true)
    protected Client getClientById(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));
    }

    protected void validateTemplateOwnership(T template, Long clientId) {
        if (!getTemplateClientId(template).equals(clientId)) {
            throw new IllegalArgumentException("Шаблон не принадлежит клиенту");
        }
    }

    protected void validateUniqueTemplateName(String templateName, Client client, Long excludeTemplateId) {
        if (isTemplateNameExists(templateName, client, excludeTemplateId)) {
            throw new IllegalArgumentException("Шаблон с таким именем уже существует");
        }
    }

    protected T getTemplateById(Long templateId) {
        return findTemplateById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));
    }

    // Абстрактные методы для реализации в дочерних классах

    protected abstract Long getTemplateId(T template);
    protected abstract Long getTemplateClientId(T template);
    protected abstract String getTemplateName(D dto);
    protected abstract Long getClientIdFromDto(D dto);
    
    protected abstract boolean isTemplateNameExists(String templateName, Client client, Long excludeTemplateId);
    protected abstract boolean isTemplateInUse(Long templateId);
    
    protected abstract T createEntityFromDto(D dto, Client client);
    protected abstract T updateEntityFromDto(T existingTemplate, D dto, Client client);
    protected abstract D createDtoFromEntity(T template);
    
    protected abstract T cloneEntity(T sourceTemplate, String newName, Client client);
    
    protected abstract Optional<T> findTemplateById(Long templateId);
    protected abstract List<T> findTemplatesByClient(Client client);
    protected abstract Page<T> findTemplatesByClient(Client client, Pageable pageable);
    
    protected abstract T saveTemplate(T template);
    protected abstract boolean existsById(Long templateId);
    protected abstract void deleteById(Long templateId);
    
    /**
     * Дополнительная валидация шаблона (переопределяется в дочерних классах при необходимости)
     */
    protected void validateTemplate(D dto) {
        // По умолчанию пустая реализация
    }
}