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

    /** ID трекера (default для pre-select в dropdown) */
    private int trackerId;

    /** ID статуса новой задачи (default для pre-select в dropdown) */
    private int statusId;

    /** ID приоритета (default для pre-select в dropdown) */
    private int priorityId;

    /** ID исполнителя (0 = не назначать) */
    private int assignedToId;

    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }

    /** URL без trailing slash */
    public String getBaseUrlNormalized() {
        return baseUrl != null ? baseUrl.stripTrailing().replaceAll("/$", "") : "";
    }
}
