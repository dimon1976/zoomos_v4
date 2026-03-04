package com.java.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.config.RedmineConfig;
import com.java.dto.RedmineCreateRequest;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedmineService {

    private final RedmineConfig config;
    private final ZoomosRedmineIssueRepository repo;
    private final ObjectMapper objectMapper;

    // GET — следуем редиректам (безопасно для GET)
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // POST/PUT — НЕ следуем редиректам: иначе POST→GET и 404
    private final HttpClient noRedirectClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    // Пул для параллельных запросов к Redmine (batch-check)
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(8);

    public boolean isEnabled() {
        return config.isEnabled();
    }

    // ──────────────────────────────────────────────────────────────────
    // Загрузка опций из Redmine API
    // ──────────────────────────────────────────────────────────────────

    public Map<String, Object> fetchOptions() {
        if (!isEnabled()) return Map.of("enabled", false);

        String base = config.getBaseUrlNormalized();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", true);

        try {
            JsonNode root = objectMapper.readTree(get(base + "/trackers.json"));
            result.put("trackers", parseIdName(root.get("trackers")));
        } catch (Exception e) {
            log.warn("Redmine fetchOptions trackers: {}", e.getMessage());
            result.put("trackers", List.of());
        }

        try {
            JsonNode root = objectMapper.readTree(get(base + "/issue_statuses.json"));
            result.put("statuses", parseIdName(root.get("issue_statuses")));
        } catch (Exception e) {
            log.warn("Redmine fetchOptions statuses: {}", e.getMessage());
            result.put("statuses", List.of());
        }

        try {
            JsonNode root = objectMapper.readTree(get(base + "/enumerations/issue_priorities.json"));
            result.put("priorities", parseIdName(root.get("issue_priorities")));
        } catch (Exception e) {
            log.warn("Redmine fetchOptions priorities: {}", e.getMessage());
            result.put("priorities", List.of());
        }

        try {
            JsonNode root = objectMapper.readTree(
                    get(base + "/projects/" + config.getProjectId() + "/memberships.json?limit=100"));
            List<Map<String, Object>> users = new ArrayList<>();
            users.add(Map.of("id", 0, "name", "— не назначен —"));
            JsonNode memberships = root.get("memberships");
            if (memberships != null && memberships.isArray()) {
                for (JsonNode m : memberships) {
                    JsonNode user = m.get("user");
                    if (user != null) {
                        users.add(Map.of("id", user.get("id").asInt(), "name", user.path("name").asText("")));
                    }
                }
            }
            result.put("users", users);
        } catch (Exception e) {
            log.warn("Redmine fetchOptions users: {}", e.getMessage());
            result.put("users", List.of(Map.of("id", 0, "name", "— не назначен —")));
        }

        try {
            JsonNode root = objectMapper.readTree(get(base + "/custom_fields.json"));
            JsonNode cfs = root.get("custom_fields");
            Map<String, Object> customFields = new LinkedHashMap<>();
            if (cfs != null && cfs.isArray()) {
                for (JsonNode cf : cfs) {
                    String name = cf.path("name").asText("");
                    int id = cf.path("id").asInt();
                    List<String> possible = new ArrayList<>();
                    JsonNode pv = cf.path("possible_values");
                    if (pv.isArray()) {
                        for (JsonNode v : pv) {
                            String val = v.isTextual() ? v.asText() : v.path("value").asText("");
                            if (!val.isBlank()) possible.add(val);
                        }
                    }
                    if ("В чем ошибка".equals(name)) {
                        customFields.put("cfError", Map.of("id", id, "possibleValues", possible));
                    } else if ("Способ выкачки".equals(name)) {
                        customFields.put("cfMethod", Map.of("id", id, "possibleValues", possible));
                    } else if ("Вариант настройки".equals(name)) {
                        customFields.put("cfVariant", Map.of("id", id, "possibleValues", possible));
                    }
                }
            }
            result.put("customFields", customFields);
        } catch (Exception e) {
            log.warn("Redmine fetchOptions custom_fields: {}", e.getMessage());
            result.put("customFields", Map.of());
        }

        result.put("defaults", Map.of(
                "trackerId", config.getTrackerId(),
                "statusId", config.getStatusId(),
                "priorityId", config.getPriorityId(),
                "assignedToId", config.getAssignedToId()
        ));

        return result;
    }

    // ──────────────────────────────────────────────────────────────────
    // Детали существующей задачи (для edit modal)
    // ──────────────────────────────────────────────────────────────────

    public Map<String, Object> getIssueDetails(int issueId) {
        if (!isEnabled()) return Map.of("error", "Redmine не настроен");
        try {
            String base = config.getBaseUrlNormalized();
            JsonNode root = objectMapper.readTree(
                    get(base + "/issues/" + issueId + ".json?include=journals"));
            JsonNode issue = root.get("issue");
            if (issue == null) throw new RuntimeException("Issue not found");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", issue.path("id").asInt());
            result.put("subject", issue.path("subject").asText(""));
            result.put("description", issue.path("description").asText(""));
            result.put("trackerId", issue.path("tracker").path("id").asInt());
            result.put("statusId", issue.path("status").path("id").asInt());
            result.put("priorityId", issue.path("priority").path("id").asInt());
            result.put("assignedToId", issue.path("assigned_to").path("id").asInt(0));

            // Значения кастомных полей: {cfId → value}
            Map<String, Object> cfValues = new LinkedHashMap<>();
            JsonNode customFields = issue.path("custom_fields");
            if (customFields.isArray()) {
                for (JsonNode cf : customFields) {
                    JsonNode val = cf.path("value");
                    // Поле может быть строкой или массивом (multiple: true)
                    String strVal = val.isArray() && val.size() > 0 ? val.get(0).asText("") : val.asText("");
                    cfValues.put(String.valueOf(cf.path("id").asInt()), strVal);
                }
            }
            result.put("customFieldValues", cfValues);

            // Последние 3 комментария (journals с непустым notes)
            List<Map<String, Object>> comments = new ArrayList<>();
            JsonNode journals = issue.path("journals");
            if (journals.isArray()) {
                for (int i = journals.size() - 1; i >= 0 && comments.size() < 3; i--) {
                    JsonNode j = journals.get(i);
                    String notes = j.path("notes").asText("").trim();
                    if (!notes.isBlank()) {
                        comments.add(Map.of(
                                "author", j.path("user").path("name").asText("?"),
                                "date",   j.path("created_on").asText("").replace("T", " ").replace("Z", ""),
                                "text",   notes.length() > 300 ? notes.substring(0, 300) + "…" : notes
                        ));
                    }
                }
            }
            result.put("comments", comments);
            return result;
        } catch (Exception e) {
            log.warn("Redmine getIssueDetails #{}: {}", issueId, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Поиск задач по имени сайта
    // ──────────────────────────────────────────────────────────────────

    public List<RedmineIssueDto> findIssuesBySite(String siteName) {
        if (!isEnabled()) return Collections.emptyList();
        try {
            String base = config.getBaseUrlNormalized();
            String encoded = URLEncoder.encode(siteName, StandardCharsets.UTF_8);
            String url = base + "/issues.json"
                    + "?project_id=" + config.getProjectId()
                    + "&subject=~" + encoded
                    + "&status_id=*"
                    + "&limit=25";
            return parseIssuesList(get(url));
        } catch (Exception e) {
            log.warn("Redmine findIssuesBySite '{}': {}", siteName, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Создание задачи
    // ──────────────────────────────────────────────────────────────────

    @Transactional
    public ZoomosRedmineIssue createIssue(RedmineCreateRequest req) {
        String description = (req.getDescription() != null && !req.getDescription().isBlank())
                ? req.getDescription()
                : buildDescription(req.getSite(), req.getCity(), req.getHistoryUrl(), req.getMatchingUrl());

        String base = config.getBaseUrlNormalized();
        String url = base + "/issues.json";

        try {
            String json = buildIssueJson(req, description);
            log.info("Redmine POST → {} | tracker={} status={} priority={} | body={}",
                    url, req.getTrackerId(), req.getStatusId(), req.getPriorityId(), json);

            // Этот Redmine-сервер всегда возвращает HTTP 404 на POST/PUT/DELETE
            // (несмотря на то что операция выполняется успешно — особенность конфигурации сервера).
            // После POST ищем созданную задачу через GET.
            postIgnoring404(url, json);

            // Ищем только что созданную задачу по теме (subject = site)
            JsonNode issue = findRecentIssueBySubject(base, req.getSite());
            if (issue == null) throw new RuntimeException("Задача не найдена после создания");

            int issueId = issue.get("id").asInt();
            String status = issue.path("status").path("name").asText("Новая");
            String issueUrl = base + "/issues/" + issueId;
            log.info("Redmine issue created: #{} — {}", issueId, req.getSite());

            ZoomosRedmineIssue entity = repo.findBySiteName(req.getSite())
                    .orElseGet(() -> ZoomosRedmineIssue.builder().siteName(req.getSite()).build());
            entity.setIssueId(issueId);
            entity.setIssueStatus(status);
            entity.setIssueUrl(issueUrl);
            entity.setUpdatedAt(LocalDateTime.now());
            if (entity.getCreatedAt() == null) entity.setCreatedAt(LocalDateTime.now());
            return repo.save(entity);
        } catch (Exception e) {
            log.error("Redmine createIssue '{}': {}", req.getSite(), e.getMessage());
            throw new RuntimeException("Не удалось создать задачу в Redmine: " + e.getMessage(), e);
        }
    }

    /**
     * Ищет последнюю задачу по точному совпадению subject.
     * Используется после POST, когда сервер возвращает 404 вместо 201.
     */
    private JsonNode findRecentIssueBySubject(String base, String subject) {
        try {
            String encoded = URLEncoder.encode(subject, StandardCharsets.UTF_8);
            String url = base + "/issues.json?project_id=" + config.getProjectId()
                    + "&subject=~" + encoded
                    + "&sort=created_on%3Adesc"
                    + "&limit=5&status_id=*";
            JsonNode root = objectMapper.readTree(get(url));
            JsonNode issues = root.get("issues");
            if (issues == null || !issues.isArray() || issues.isEmpty()) return null;
            // Берём первую задачу (sort=created_on:desc → самая свежая)
            return issues.get(0);
        } catch (Exception e) {
            log.warn("Redmine findRecentIssueBySubject '{}': {}", subject, e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Обновление задачи
    // ──────────────────────────────────────────────────────────────────

    @Transactional
    public void updateIssue(int issueId, RedmineCreateRequest req) {
        String base = config.getBaseUrlNormalized();
        String url = base + "/issues/" + issueId + ".json";

        try {
            Map<String, Object> issue = new LinkedHashMap<>();
            if (req.getTrackerId() > 0)    issue.put("tracker_id", req.getTrackerId());
            if (req.getStatusId() > 0)     issue.put("status_id", req.getStatusId());
            if (req.getPriorityId() > 0)   issue.put("priority_id", req.getPriorityId());
            if (req.getAssignedToId() > 0) issue.put("assigned_to_id", req.getAssignedToId());
            else                            issue.put("assigned_to_id", "");   // сбросить исполнителя
            if (req.getDescription() != null) issue.put("description", req.getDescription());
            if (req.getNotes() != null && !req.getNotes().isBlank()) issue.put("notes", req.getNotes());
            if (req.getCustomFields() != null && !req.getCustomFields().isEmpty()) {
                issue.put("custom_fields", req.getCustomFields());
            }

            String json = objectMapper.writeValueAsString(Map.of("issue", issue));
            log.info("Redmine PUT → {} | status={}", url, req.getStatusId());
            // Сервер возвращает 404 даже при успешном обновлении — игнорируем
            putIgnoring404(url, json);

            // Обновляем время в нашей БД
            repo.findBySiteName(req.getSite()).ifPresent(entity -> {
                entity.setUpdatedAt(LocalDateTime.now());
                repo.save(entity);
            });
        } catch (Exception e) {
            log.error("Redmine updateIssue #{}: {}", issueId, e.getMessage());
            throw new RuntimeException("Не удалось обновить задачу в Redmine: " + e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Загрузка из БД + обновление статусов
    // ──────────────────────────────────────────────────────────────────

    /** Только из БД, без API-запросов — для быстрого рендера страницы */
    public List<ZoomosRedmineIssue> findAllBySiteNames(Collection<String> siteNames) {
        if (siteNames == null || siteNames.isEmpty()) return Collections.emptyList();
        return repo.findAllBySiteNameIn(siteNames);
    }

    @Transactional
    public Map<String, ZoomosRedmineIssue> getIssuesMapForSites(Collection<String> siteNames) {
        if (!isEnabled() || siteNames.isEmpty()) return Collections.emptyMap();
        List<ZoomosRedmineIssue> existing = repo.findAllBySiteNameIn(siteNames);
        if (existing.isEmpty()) return Collections.emptyMap();
        refreshStatuses(existing);
        return existing.stream().collect(Collectors.toMap(ZoomosRedmineIssue::getSiteName, e -> e));
    }

    @Transactional
    public void refreshStatuses(List<ZoomosRedmineIssue> issues) {
        if (!isEnabled()) return;
        String base = config.getBaseUrlNormalized();
        for (ZoomosRedmineIssue issue : issues) {
            try {
                JsonNode root = objectMapper.readTree(get(base + "/issues/" + issue.getIssueId() + ".json"));
                JsonNode statusNode = root.path("issue").path("status");
                String status = statusNode.path("name").asText();
                boolean closed = statusNode.path("is_closed").asBoolean(false);
                boolean changed = false;
                if (!status.isBlank() && !status.equals(issue.getIssueStatus())) {
                    issue.setIssueStatus(status);
                    changed = true;
                }
                if (closed != issue.isClosed()) {
                    issue.setClosed(closed);
                    changed = true;
                }
                if (changed) {
                    issue.setUpdatedAt(LocalDateTime.now());
                    repo.save(issue);
                }
            } catch (Exception e) {
                log.warn("Redmine refreshStatus #{}: {}", issue.getIssueId(), e.getMessage());
            }
        }
    }

    /**
     * Параллельная проверка Redmine для списка сайтов.
     * Возвращает Map<siteName, latestIssue> — только для сайтов, у которых нашлась задача.
     * Также сохраняет/обновляет найденные задачи в БД.
     */
    @Transactional
    public Map<String, Object> checkBatch(List<String> sites) {
        if (!isEnabled() || sites == null || sites.isEmpty()) return Collections.emptyMap();

        List<CompletableFuture<Map.Entry<String, Object>>> futures = sites.stream()
                .map(site -> CompletableFuture.supplyAsync(() -> {
                    try {
                        List<RedmineIssueDto> found = findIssuesBySite(site);
                        if (found.isEmpty()) return null;
                        RedmineIssueDto latest = found.get(0);
                        // Сохраняем/обновляем в БД
                        ZoomosRedmineIssue entity = repo.findBySiteName(site)
                                .orElseGet(() -> ZoomosRedmineIssue.builder().siteName(site).build());
                        entity.setIssueId(latest.getId());
                        entity.setIssueStatus(latest.getStatusName());
                        entity.setClosed(latest.isClosed());
                        entity.setIssueUrl(latest.getUrl());
                        entity.setUpdatedAt(LocalDateTime.now());
                        if (entity.getCreatedAt() == null) entity.setCreatedAt(LocalDateTime.now());
                        repo.save(entity);
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("id", latest.getId());
                        data.put("url", latest.getUrl());
                        data.put("statusName", latest.getStatusName());
                        data.put("isClosed", latest.isClosed());
                        return Map.entry(site, (Object) data);
                    } catch (Exception e) {
                        log.warn("Redmine checkBatch '{}': {}", site, e.getMessage());
                        return null;
                    }
                }, batchExecutor))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<String, Object>> f : futures) {
            try {
                Map.Entry<String, Object> entry = f.get();
                if (entry != null) result.put(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.warn("Redmine checkBatch future: {}", e.getMessage());
            }
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────
    // Утилиты
    // ──────────────────────────────────────────────────────────────────

    private String buildDescription(String site, String city, String historyUrl, String matchingUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("Сайт: ").append(site).append("\n");
        if (city != null && !city.isBlank()) sb.append("Город: ").append(city).append("\n");
        if (historyUrl != null && !historyUrl.isBlank()) sb.append("\nИстория выкачки: ").append(historyUrl);
        if (matchingUrl != null && !matchingUrl.isBlank()) sb.append("\nМатчинг: ").append(matchingUrl);
        return sb.toString();
    }

    private String buildIssueJson(RedmineCreateRequest req, String description) throws Exception {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("project_id", config.getProjectId());   // обязателен для POST /issues.json
        issue.put("tracker_id", req.getTrackerId() > 0 ? req.getTrackerId() : config.getTrackerId());
        issue.put("subject", req.getSite());
        issue.put("description", description);
        issue.put("status_id", req.getStatusId() > 0 ? req.getStatusId() : config.getStatusId());
        issue.put("priority_id", req.getPriorityId() > 0 ? req.getPriorityId() : config.getPriorityId());
        if (req.getAssignedToId() > 0) issue.put("assigned_to_id", req.getAssignedToId());
        if (req.getCustomFields() != null && !req.getCustomFields().isEmpty()) {
            issue.put("custom_fields", req.getCustomFields());
        }
        return objectMapper.writeValueAsString(Map.of("issue", issue));
    }

    private List<Map<String, Object>> parseIdName(JsonNode arr) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (arr == null || !arr.isArray()) return list;
        for (JsonNode node : arr) {
            list.add(Map.of("id", node.path("id").asInt(), "name", node.path("name").asText("")));
        }
        return list;
    }

    private List<RedmineIssueDto> parseIssuesList(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode issues = root.get("issues");
        if (issues == null || !issues.isArray()) return Collections.emptyList();
        String base = config.getBaseUrlNormalized();
        List<RedmineIssueDto> result = new ArrayList<>();
        for (JsonNode node : issues) {
            int id = node.get("id").asInt();
            result.add(RedmineIssueDto.builder()
                    .id(id)
                    .subject(node.path("subject").asText())
                    .statusName(node.path("status").path("name").asText())
                    .isClosed(node.path("status").path("is_closed").asBoolean(false))
                    .url(base + "/issues/" + id)
                    .build());
        }
        return result;
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Redmine-API-Key", config.getApiKey())
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String post(String url, String json) throws Exception {
        return sendMutating("POST", url, json, false);
    }

    /** POST, который не бросает исключение при 404 с пустым телом (особенность этого Redmine-сервера) */
    private void postIgnoring404(String url, String json) throws Exception {
        sendMutating("POST", url, json, true);
    }

    private String put(String url, String json) throws Exception {
        return sendMutating("PUT", url, json, false);
    }

    /** PUT, который не бросает исключение при 404 с пустым телом */
    private void putIgnoring404(String url, String json) throws Exception {
        sendMutating("PUT", url, json, true);
    }

    /**
     * Выполняет POST или PUT без следования редиректам.
     * Аутентификация через ?key= (URL-параметр) — надёжнее заголовка.
     *
     * @param ignore404EmptyBody если true — 404 с пустым телом не считается ошибкой
     *                           (этот Redmine-сервер всегда возвращает 404 на мутирующих операциях,
     *                           при этом операция фактически выполняется)
     */
    private String sendMutating(String method, String url, String json, boolean ignore404EmptyBody) throws Exception {
        String urlWithKey = url.contains("?")
                ? url + "&key=" + config.getApiKey()
                : url + "?key=" + config.getApiKey();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(urlWithKey))
                .header("X-Redmine-API-Key", config.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15));

        if ("PUT".equals(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        }

        HttpResponse<String> response = noRedirectClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        log.info("Redmine {} → HTTP {} (body length={})", method, response.statusCode(),
                response.body() == null ? 0 : response.body().length());

        if (response.statusCode() >= 300 && response.statusCode() < 400) {
            String location = response.headers().firstValue("Location").orElse("(no Location header)");
            log.error("Redmine {} redirect: {} → {}", method, url, location);
            throw new RuntimeException("Redmine redirect " + response.statusCode()
                    + ". Проверьте redmine.base-url в application.properties");
        }

        if (response.statusCode() >= 400) {
            String body = response.body();
            // Этот Redmine-сервер возвращает 404 с пустым телом даже при успехе
            if (ignore404EmptyBody && response.statusCode() == 404 && (body == null || body.isBlank())) {
                log.info("Redmine {} — игнорируем 404 с пустым телом (операция выполнена на сервере)", method);
                return "";
            }
            log.error("Redmine {} error: HTTP {} body=[{}]", method, response.statusCode(), body);
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + body);
        }

        return response.body();
    }
}
