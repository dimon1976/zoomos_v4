package com.java.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "redmine")
@Data
public class RedmineConfig {

    /** Base URL Redmine (напр. https://redmine.company.com). Пустой — интеграция отключена. */
    private String baseUrl = "";

    /** API-ключ (X-Redmine-API-Key). Пустой — интеграция отключена. */
    private String apiKey = "";

    /** ID проекта в Redmine */
    private int projectId;

    /** ID трекера */
    private int trackerId;

    /** Отображаемое имя трекера */
    private String trackerName = "";

    /** ID статуса новой задачи */
    private int statusId;

    /** Отображаемое имя статуса */
    private String statusName = "";

    /** ID приоритета */
    private int priorityId;

    /** Отображаемое имя приоритета */
    private String priorityName = "";

    /** ID исполнителя (0 = не назначать) */
    private int assignedToId;

    /** ID custom field "В чем ошибка" (0 = не передавать) */
    private int cfErrorId;

    /** ID custom field "Способ выкачки" (0 = не передавать) */
    private int cfParsingMethodId;

    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }
}
