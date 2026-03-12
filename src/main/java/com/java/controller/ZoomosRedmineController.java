package com.java.controller;

import com.java.dto.RedmineCreateRequest;
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

    /** Динамические опции: трекеры, статусы, приоритеты, пользователи, custom fields + defaults */
    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> getOptions() {
        return ResponseEntity.ok(redmineService.fetchOptions());
    }

    /** Детали существующей задачи (для edit modal) */
    @GetMapping("/issue/{issueId}")
    public ResponseEntity<Map<String, Object>> getIssueDetails(@PathVariable int issueId) {
        return ResponseEntity.ok(redmineService.getIssueDetails(issueId));
    }

    /** Пакетная проверка Redmine для списка сайтов (параллельно) */
    @GetMapping("/check-batch")
    public ResponseEntity<Map<String, Object>> checkBatch(@RequestParam List<String> sites) {
        if (!redmineService.isEnabled()) return ResponseEntity.ok(Map.of("enabled", false));
        return ResponseEntity.ok(redmineService.checkBatch(sites));
    }

    /** Существующие задачи по имени сайта */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkIssue(@RequestParam String site) {
        if (!redmineService.isEnabled()) return ResponseEntity.ok(Map.of("enabled", false));
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", true);
        result.put("existing", redmineService.findIssuesBySite(site));
        return ResponseEntity.ok(result);
    }

    /** Создание задачи */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createIssue(@RequestBody RedmineCreateRequest req) {
        if (!redmineService.isEnabled())
            return ResponseEntity.ok(Map.of("success", false, "error", "Redmine не настроен"));
        try {
            ZoomosRedmineIssue created = redmineService.createIssue(req);

            String shortMessage = extractShortMessage(req);

            Map<String, Object> issueData = new HashMap<>();
            issueData.put("id", created.getIssueId());
            issueData.put("status", created.getIssueStatus());
            issueData.put("url", created.getIssueUrl());
            issueData.put("site", req.getSite());
            issueData.put("shortMessage", shortMessage);

            return ResponseEntity.ok(Map.of("success", true, "issue", issueData));
        } catch (Exception e) {
            log.error("Ошибка создания задачи Redmine для {}: {}", req.getSite(), e.getMessage());
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /** Обновление существующей задачи */
    @PutMapping("/update/{issueId}")
    public ResponseEntity<Map<String, Object>> updateIssue(
            @PathVariable int issueId,
            @RequestBody RedmineCreateRequest req) {
        if (!redmineService.isEnabled())
            return ResponseEntity.ok(Map.of("success", false, "error", "Redmine не настроен"));
        try {
            ZoomosRedmineIssue updated = redmineService.updateIssue(issueId, req);
            Map<String, Object> issueData = new HashMap<>();
            issueData.put("id", issueId);
            issueData.put("url", updated != null ? updated.getIssueUrl() : "");
            issueData.put("statusName", updated != null ? updated.getIssueStatus() : "");
            issueData.put("isClosed", updated != null && updated.isClosed());
            return ResponseEntity.ok(Map.of("success", true, "issue", issueData));
        } catch (Exception e) {
            log.error("Ошибка обновления задачи Redmine #{}: {}", issueId, e.getMessage());
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /** Удалить локальную запись задачи из БД (когда задача удалена в Redmine) */
    @DeleteMapping("/local-delete/{site}")
    public ResponseEntity<Map<String, Object>> localDelete(@PathVariable String site) {
        try {
            redmineService.deleteLocalIssue(site);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private String extractShortMessage(RedmineCreateRequest req) {
        // Явно переданное краткое описание проблемы ("В чем ошибка") с фронтенда
        if (req.getShortMessage() != null && !req.getShortMessage().isBlank()) {
            String s = req.getShortMessage();
            return s.length() > 80 ? s.substring(0, 80) + "..." : s;
        }
        return req.getSite();
    }
}
