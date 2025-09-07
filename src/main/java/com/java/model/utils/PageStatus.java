package com.java.model.utils;

public enum PageStatus {
    OK("Успешно получено"),
    REDIRECT("Редирект выполнен"), 
    BLOCKED("Заблокировано антиботом"),
    NOT_FOUND("Страница не найдена"),
    ERROR("Техническая ошибка");
    
    private final String description;
    
    PageStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}