package com.java.controller;

import com.java.config.RedmineConfig;
import com.java.dto.RedmineIssueDto;
import com.java.model.entity.ZoomosRedmineIssue;
import com.java.service.RedmineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/zoomos/redmine")
@Slf4j
@RequiredArgsConstructor
public class ZoomosRedmineController {

    private final RedmineService redmineService;
    private final RedmineConfig redmineConfig;

    /**
     * Конфигурация Redmine для фронтенда (отображаемые имена полей, флаг enabled).
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", redmineService.isEnabled());
        if (redmineService.isEnabled()) {
            result.put("trackerName", redmineConfig.getTrackerName());
            result.put("statusName", redmineConfig.getStatusName());
            result.put("priorityName", redmineConfig.getPriorityName());
            result.put("projectId", redmineConfig.getProjectId());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Проверка существующих задач в Redmine по имени сайта + превью полей новой задачи.
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkIssue(
            @RequestParam String site,
            @RequestParam(required = false, defaultValue = "") String city,
            @RequestParam(required = false, defaultValue = "") String message,
            @RequestParam(required = false, defaultValue = "") String checkType,
            @RequestParam(required = false, defaultValue = "") String historyUrl,
            @RequestParam(required = false, defaultValue = "") String matchingUrl) {

        if (!redmineService.isEnabled()) {
            return ResponseEntity.ok(Map.of("enabled", false));
        }

        List<RedmineIssueDto> existing = redmineService.findIssuesBySite(site);

        // Превью полей для новой задачи
        Map<String, Object> preview = new HashMap<>();
        preview.put("subject", site);
        preview.put("tracker", redmineConfig.getTrackerName());
        preview.put("status", redmineConfig.getStatusName());
        preview.put("priority", redmineConfig.getPriorityName());
        preview.put("errorMessage", message);
        preview.put("checkType", checkType);
        preview.put("description", buildDescriptionPreview(site, city, message, historyUrl, matchingUrl));

        Map<String, Object> result = new HashMap<>();
        result.put("enabled", true);
        result.put("existing", existing);
        result.put("preview", preview);
        return ResponseEntity.ok(result);
    }

    /**
     * Создание задачи в Redmine.
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createIssue(
            @RequestParam String site,
            @RequestParam(required = false, defaultValue = "") String city,
            @RequestParam(required = false, defaultValue = "") String message,
            @RequestParam(required = false, defaultValue = "") String checkType,
            @RequestParam(required = false, defaultValue = "") String historyUrl,
            @RequestParam(required = false, defaultValue = "") String matchingUrl) {

        if (!redmineService.isEnabled()) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Redmine не настроен"));
        }

        try {
            ZoomosRedmineIssue created = redmineService.createIssue(
                    site, city, message, checkType, historyUrl, matchingUrl);

            Map<String, Object> issueData = new HashMap<>();
            issueData.put("id", created.getIssueId());
            issueData.put("status", created.getIssueStatus());
            issueData.put("url", created.getIssueUrl());

            return ResponseEntity.ok(Map.of("success", true, "issue", issueData));
        } catch (Exception e) {
            log.error("Ошибка создания задачи Redmine для {}: {}", site, e.getMessage());
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private String buildDescriptionPreview(String site, String city, String message,
                                           String historyUrl, String matchingUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("Сайт: ").append(site).append("\n");
        if (!city.isBlank()) sb.append("Город: ").append(city).append("\n");
        sb.append("Проблема: ").append(message).append("\n");
        if (!historyUrl.isBlank()) sb.append("\nИстория выкачки: ").append(historyUrl);
        if (!matchingUrl.isBlank()) sb.append("\nМатчинг: ").append(matchingUrl);
        return sb.toString();
    }
}
