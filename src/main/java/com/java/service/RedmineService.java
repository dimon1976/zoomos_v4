package com.java.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.config.RedmineConfig;
import com.java.dto.RedmineIssueDto;
import com.java.model.entity.ZoomosRedmineIssue;
import com.java.repository.ZoomosRedmineIssueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedmineService {

    private final RedmineConfig config;
    private final ZoomosRedmineIssueRepository repo;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public boolean isEnabled() {
        return config.isEnabled();
    }

    // ──────────────────────────────────────────────────────────────────
    // Поиск задач в Redmine API по имени сайта
    // ──────────────────────────────────────────────────────────────────

    public List<RedmineIssueDto> findIssuesBySite(String siteName) {
        if (!isEnabled()) return Collections.emptyList();
        try {
            String encoded = URLEncoder.encode(siteName, StandardCharsets.UTF_8);
            String url = config.getBaseUrl() + "/issues.json"
                    + "?project_id=" + config.getProjectId()
                    + "&subject=~" + encoded
                    + "&status_id=*"
                    + "&limit=25";
            String body = get(url);
            return parseIssuesList(body);
        } catch (Exception e) {
            log.warn("Redmine: ошибка поиска задач для '{}': {}", siteName, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Создание задачи в Redmine + сохранение в БД
    // ──────────────────────────────────────────────────────────────────

    @Transactional
    public ZoomosRedmineIssue createIssue(String site, String city, String message,
                                          String checkType, String historyUrl, String matchingUrl) {
        String description = buildDescription(site, city, message, historyUrl, matchingUrl);
        String json = buildCreateJson(site, description, message, checkType);

        try {
            String url = config.getBaseUrl() + "/issues.json";
            String responseBody = post(url, json);
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode issue = root.get("issue");
            if (issue == null) throw new RuntimeException("Пустой ответ от Redmine");

            int issueId = issue.get("id").asInt();
            String status = issue.path("status").path("name").asText("Новая");
            String issueUrl = config.getBaseUrl() + "/issues/" + issueId;

            ZoomosRedmineIssue entity = repo.findBySiteName(site)
                    .orElseGet(() -> ZoomosRedmineIssue.builder().siteName(site).build());
            entity.setIssueId(issueId);
            entity.setIssueStatus(status);
            entity.setIssueUrl(issueUrl);
            entity.setUpdatedAt(LocalDateTime.now());
            if (entity.getCreatedAt() == null) entity.setCreatedAt(LocalDateTime.now());

            return repo.save(entity);
        } catch (Exception e) {
            log.error("Redmine: ошибка создания задачи для '{}': {}", site, e.getMessage());
            throw new RuntimeException("Не удалось создать задачу в Redmine: " + e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Загрузка из БД + обновление статусов из Redmine API
    // ──────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, ZoomosRedmineIssue> getIssuesMapForSites(Collection<String> siteNames) {
        if (!isEnabled() || siteNames.isEmpty()) return Collections.emptyMap();

        List<ZoomosRedmineIssue> existing = repo.findAllBySiteNameIn(siteNames);
        if (existing.isEmpty()) return Collections.emptyMap();

        // Обновляем статусы из Redmine API
        refreshStatuses(existing);

        return existing.stream()
                .collect(Collectors.toMap(ZoomosRedmineIssue::getSiteName, e -> e));
    }

    @Transactional
    public void refreshStatuses(List<ZoomosRedmineIssue> issues) {
        if (!isEnabled()) return;
        for (ZoomosRedmineIssue issue : issues) {
            try {
                String url = config.getBaseUrl() + "/issues/" + issue.getIssueId() + ".json";
                String body = get(url);
                JsonNode root = objectMapper.readTree(body);
                String status = root.path("issue").path("status").path("name").asText();
                if (!status.isBlank() && !status.equals(issue.getIssueStatus())) {
                    issue.setIssueStatus(status);
                    issue.setUpdatedAt(LocalDateTime.now());
                    repo.save(issue);
                }
            } catch (Exception e) {
                log.warn("Redmine: не удалось обновить статус issue #{}: {}", issue.getIssueId(), e.getMessage());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Утилиты
    // ──────────────────────────────────────────────────────────────────

    private String buildDescription(String site, String city, String message,
                                    String historyUrl, String matchingUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("Сайт: ").append(site).append("\n");
        if (city != null && !city.isBlank()) sb.append("Город: ").append(city).append("\n");
        sb.append("Проблема: ").append(message).append("\n");
        if (historyUrl != null && !historyUrl.isBlank()) {
            sb.append("\nИстория выкачки: ").append(historyUrl);
        }
        if (matchingUrl != null && !matchingUrl.isBlank()) {
            sb.append("\nМатчинг: ").append(matchingUrl);
        }
        return sb.toString();
    }

    private String buildCreateJson(String site, String description, String message, String checkType) {
        try {
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("project_id", config.getProjectId());
            issue.put("tracker_id", config.getTrackerId());
            issue.put("subject", site);
            issue.put("description", description);
            issue.put("status_id", config.getStatusId());
            issue.put("priority_id", config.getPriorityId());
            if (config.getAssignedToId() > 0) {
                issue.put("assigned_to_id", config.getAssignedToId());
            }

            List<Map<String, Object>> customFields = new ArrayList<>();
            if (config.getCfErrorId() > 0) {
                customFields.add(Map.of("id", config.getCfErrorId(), "value", message));
            }
            if (config.getCfParsingMethodId() > 0) {
                String method = checkType != null ? checkType : "";
                customFields.add(Map.of("id", config.getCfParsingMethodId(), "value", method));
            }
            if (!customFields.isEmpty()) {
                issue.put("custom_fields", customFields);
            }

            return objectMapper.writeValueAsString(Map.of("issue", issue));
        } catch (Exception e) {
            throw new RuntimeException("Ошибка формирования JSON для Redmine", e);
        }
    }

    private List<RedmineIssueDto> parseIssuesList(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode issues = root.get("issues");
        if (issues == null || !issues.isArray()) return Collections.emptyList();

        List<RedmineIssueDto> result = new ArrayList<>();
        for (JsonNode node : issues) {
            int id = node.get("id").asInt();
            result.add(RedmineIssueDto.builder()
                    .id(id)
                    .subject(node.path("subject").asText())
                    .statusName(node.path("status").path("name").asText())
                    .url(config.getBaseUrl() + "/issues/" + id)
                    .build());
        }
        return result;
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Redmine-API-Key", config.getApiKey())
                .header("Content-Type", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String post(String url, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Redmine-API-Key", config.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }
}
