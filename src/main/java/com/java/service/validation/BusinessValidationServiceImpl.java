package com.java.service.validation;

import com.java.model.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Реализация сервиса валидации бизнес-правил.
 * Обеспечивает проверку бизнес-логики и правил системы.
 */
@Slf4j
@Service
public class BusinessValidationServiceImpl implements BusinessValidationService {
    
    @Override
    public void validateClient(Client client) throws ValidationException {
        if (client == null) {
            throw new ValidationException("client", null, "Клиент не может быть null");
        }
        
        // Валидация названия клиента
        if (client.getName() == null || client.getName().trim().isEmpty()) {
            throw new ValidationException("name", client.getName(), "Название клиента не может быть пустым");
        }
        
        // Валидация длины названия
        if (client.getName().length() > 255) {
            throw new ValidationException("name", client.getName(), 
                "Название клиента не может быть длиннее 255 символов");
        }
        
        // Валидация описания (если есть)
        if (client.getDescription() != null && client.getDescription().length() > 1000) {
            throw new ValidationException("description", client.getDescription(), 
                "Описание клиента не может быть длиннее 1000 символов");
        }
        
        log.debug("Клиент {} успешно прошел валидацию", client.getName());
    }
    
    @Override
    public void validateTemplate(Object template) throws ValidationException {
        if (template == null) {
            throw new ValidationException("template", null, "Шаблон не может быть null");
        }
        
        // Базовая валидация шаблона
        // В будущем здесь будет более детальная валидация в зависимости от типа шаблона
        log.debug("Шаблон {} успешно прошел валидацию", template.getClass().getSimpleName());
    }
    
    @Override
    public void validateOperation(String operationType, Long clientId, Long templateId) throws ValidationException {
        // Валидация типа операции
        if (operationType == null || operationType.trim().isEmpty()) {
            throw new ValidationException("operationType", operationType, "Тип операции не может быть пустым");
        }
        
        String normalizedType = operationType.toUpperCase().trim();
        if (!normalizedType.equals("IMPORT") && !normalizedType.equals("EXPORT") && !normalizedType.equals("PROCESS")) {
            throw new ValidationException("operationType", operationType, 
                "Недопустимый тип операции. Допустимые значения: IMPORT, EXPORT, PROCESS");
        }
        
        // Валидация ID клиента
        if (clientId == null || clientId <= 0) {
            throw new ValidationException("clientId", clientId, "ID клиента должен быть положительным числом");
        }
        
        // Валидация ID шаблона (может быть null для некоторых операций)
        if (templateId != null && templateId <= 0) {
            throw new ValidationException("templateId", templateId, "ID шаблона должен быть положительным числом");
        }
        
        log.debug("Операция {} для клиента {} с шаблоном {} прошла валидацию", 
                 operationType, clientId, templateId);
    }
    
    @Override
    public void validatePermissions(String operation, Long resourceId) throws ValidationException {
        // Валидация операции
        if (operation == null || operation.trim().isEmpty()) {
            throw new ValidationException("operation", operation, "Операция не может быть пустой");
        }
        
        // Валидация ID ресурса
        if (resourceId == null || resourceId <= 0) {
            throw new ValidationException("resourceId", resourceId, 
                "ID ресурса должен быть положительным числом");
        }
        
        // Здесь может быть добавлена логика проверки прав доступа
        // например, проверка роли пользователя, принадлежности ресурса и т.д.
        
        log.debug("Права доступа на операцию {} для ресурса {} прошли валидацию", 
                 operation, resourceId);
    }
    
    @Override
    public <T> void validate(T object) throws ValidationException {
        if (object instanceof Client) {
            validateClient((Client) object);
        } else if (object instanceof String) {
            // Для строковых объектов выполняем базовую валидацию
            String str = (String) object;
            if (str.trim().isEmpty()) {
                throw new ValidationException("string", str, "Строка не может быть пустой");
            }
        } else {
            // Для других объектов используем validateTemplate
            validateTemplate(object);
        }
    }
    
    @Override
    public boolean canValidate(Class<?> objectClass) {
        return Client.class.isAssignableFrom(objectClass) ||
               String.class.equals(objectClass) ||
               Object.class.isAssignableFrom(objectClass); // Может валидировать любые объекты как шаблоны
    }
}