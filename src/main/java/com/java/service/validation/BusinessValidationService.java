package com.java.service.validation;

import com.java.model.Client;

/**
 * Интерфейс для валидации бизнес-правил.
 * Обеспечивает проверку бизнес-логики и правил системы.
 */
public interface BusinessValidationService extends ValidationService {
    
    /**
     * Валидирует бизнес-правила для клиента.
     * 
     * @param client клиент для валидации
     * @throws ValidationException если бизнес-правила нарушены
     */
    void validateClient(Client client) throws ValidationException;
    
    /**
     * Валидирует шаблон импорта/экспорта.
     * 
     * @param template шаблон для валидации
     * @throws ValidationException если шаблон некорректен
     */
    void validateTemplate(Object template) throws ValidationException;
    
    /**
     * Валидирует бизнес-правила для операции.
     * 
     * @param operationType тип операции (IMPORT/EXPORT)
     * @param clientId ID клиента
     * @param templateId ID шаблона
     * @throws ValidationException если операция не может быть выполнена
     */
    void validateOperation(String operationType, Long clientId, Long templateId) throws ValidationException;
    
    /**
     * Валидирует разрешения пользователя на выполнение операции.
     * 
     * @param operation тип операции
     * @param resourceId ID ресурса
     * @throws ValidationException если доступ запрещен
     */
    void validatePermissions(String operation, Long resourceId) throws ValidationException;
}