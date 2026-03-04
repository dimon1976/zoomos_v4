package com.java.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RedmineCreateRequest {
    private String site;
    private String city;
    private String historyUrl;
    private String matchingUrl;
    private int trackerId;
    private int statusId;
    private int priorityId;
    private int assignedToId;
    /** Описание задачи (редактируемое, приходит с фронтенда) */
    private String description;
    /** Краткое описание проблемы (значение "В чем ошибка") для блока копирования */
    private String shortMessage;
    /** Текст нового комментария (journal note) при обновлении задачи */
    private String notes;
    /** Список custom fields: [{id: 7, value: "Нет данных"}, ...] */
    private List<Map<String, Object>> customFields;
}
