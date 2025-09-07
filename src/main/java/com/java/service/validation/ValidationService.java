package com.java.service.validation;

/**
 * Базовый интерфейс для всех сервисов валидации.
 * Обеспечивает единый подход к валидации в рамках Validation Layer.
 */
public interface ValidationService {
    
    /**
     * Выполняет валидацию объекта.
     * 
     * @param object объект для валидации
     * @param <T> тип объекта
     * @throws ValidationException если валидация не прошла
     */
    <T> void validate(T object) throws ValidationException;
    
    /**
     * Проверяет, можно ли валидировать объект данного типа.
     * 
     * @param objectClass класс объекта
     * @return true если сервис может валидировать объекты данного типа
     */
    boolean canValidate(Class<?> objectClass);
}