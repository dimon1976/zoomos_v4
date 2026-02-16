package com.java.controller;

import com.java.model.entity.*;
import com.java.repository.ZoomosCheckRunRepository;
import com.java.repository.ZoomosParsingStatsRepository;
import com.java.service.ZoomosCheckService;
import com.java.service.ZoomosParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Контроллер "Анализ выкачки" — парсинг данных с export.zoomos.by.
 * Маршруты: /zoomos/*
 */
@Controller
@RequestMapping("/zoomos")
@RequiredArgsConstructor
@Slf4j
public class ZoomosAnalysisController {

    private final ZoomosParserService parserService;
    private final ZoomosCheckService checkService;
    private final ZoomosCheckRunRepository checkRunRepository;
    private final ZoomosParsingStatsRepository parsingStatsRepository;

    @GetMapping({"", "/"})
    public String index(Model model) {
        List<ZoomosShop> shops = parserService.getAllShops();
        // Map<shopId, List<ZoomosCityId>> для удобного доступа в шаблоне
        Map<Long, List<ZoomosCityId>> cityIdsMap = new java.util.LinkedHashMap<>();
        for (ZoomosShop shop : shops) {
            cityIdsMap.put(shop.getId(), parserService.getCityIds(shop.getId()));
        }
        model.addAttribute("shops", shops);
        model.addAttribute("cityIdsMap", cityIdsMap);
        return "zoomos/index";
    }

    // =========================================================================
    // Управление магазинами
    // =========================================================================

    @PostMapping("/shops/add")
    public String addShop(@RequestParam String shopName, RedirectAttributes ra) {
        try {
            parserService.addShop(shopName);
            ra.addFlashAttribute("success", "Магазин '" + shopName.trim() + "' добавлен");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/zoomos";
    }

    @PostMapping("/shops/{id}/delete")
    public String deleteShop(@PathVariable Long id, RedirectAttributes ra) {
        try {
            parserService.deleteShop(id);
            ra.addFlashAttribute("success", "Магазин удалён");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Ошибка удаления: " + e.getMessage());
        }
        return "redirect:/zoomos";
    }

    // =========================================================================
    // Синхронизация (парсинг)
    // =========================================================================

    @PostMapping("/shops/{shopName}/sync")
    public String syncShop(@PathVariable String shopName, RedirectAttributes ra) {
        try {
            String result = parserService.syncShopSettings(shopName);
            ra.addFlashAttribute("success", result);
        } catch (Exception e) {
            log.error("Ошибка синхронизации {}", shopName, e);
            ra.addFlashAttribute("error", "Ошибка: " + e.getMessage());
        }
        return "redirect:/zoomos";
    }

    // =========================================================================
    // AJAX: управление city_ids
    // =========================================================================

    @PostMapping("/city-ids/{id}/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleCityId(@PathVariable Long id) {
        try {
            ZoomosCityId entry = parserService.toggleCityId(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "isActive", Boolean.TRUE.equals(entry.getIsActive())
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/city-ids/{id}/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateCityIds(@PathVariable Long id,
                                                              @RequestParam String cityIds) {
        try {
            parserService.updateCityIds(id, cityIds.trim());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/city-ids/{id}/check-type")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateCheckType(@PathVariable Long id,
                                                                @RequestParam String checkType) {
        try {
            parserService.updateCheckType(id, checkType.trim());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // =========================================================================
    // Проверка выкачки
    // =========================================================================

    @GetMapping("/check")
    public String checkPage(Model model) {
        model.addAttribute("shops", parserService.getAllShops());
        return "zoomos/check";
    }

    @PostMapping("/check/run")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> runCheck(
            @RequestParam Long shopId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "10") int dropThreshold,
            @RequestParam(defaultValue = "30") int errorGrowthThreshold) {
        log.info("runCheck: shopId={} dateFrom='{}' dateTo='{}'", shopId, dateFrom, dateTo);
        if (dateFrom == null || dateFrom.isBlank() || dateTo == null || dateTo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Не указаны даты проверки"));
        }
        LocalDate from, to;
        try {
            from = LocalDate.parse(dateFrom);
            to   = LocalDate.parse(dateTo);
        } catch (Exception e) {
            log.error("Ошибка парсинга дат: dateFrom='{}' dateTo='{}'", dateFrom, dateTo);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Неверный формат дат: " + dateFrom + " / " + dateTo));
        }
        try {
            String operationId = UUID.randomUUID().toString();

            // Запускаем в фоне, чтобы не блокировать HTTP
            CompletableFuture.runAsync(() -> {
                try {
                    checkService.runCheck(shopId, from, to, dropThreshold, errorGrowthThreshold, operationId);
                } catch (Exception e) {
                    log.error("Ошибка фоновой проверки: {}", e.getMessage(), e);
                }
            });

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "operationId", operationId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/check/results/{runId}")
    public String checkResults(@PathVariable Long runId, Model model) {
        ZoomosCheckRun run = checkRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Проверка не найдена: " + runId));

        List<ZoomosParsingStats> stats = parsingStatsRepository
                .findByCheckRunIdOrderBySiteNameAscCityNameAsc(runId);

        int dropThreshold = run.getDropThreshold() != null ? run.getDropThreshold() : 10;
        int errorGrowthThreshold = run.getErrorGrowthThreshold() != null ? run.getErrorGrowthThreshold() : 30;

        // Группируем по site+city для оценки динамики
        Map<String, List<ZoomosParsingStats>> bySiteCity = stats.stream()
                .collect(Collectors.groupingBy(s ->
                        s.getSiteName() + "|" + (s.getCityName() != null ? s.getCityName() : "")));

        List<Map<String, Object>> issues = new ArrayList<>();

        // Статус каждой связки site+city (для оценки динамики)
        Map<String, String> siteCityStatuses = new LinkedHashMap<>();
        for (Map.Entry<String, List<ZoomosParsingStats>> entry : bySiteCity.entrySet()) {
            List<ZoomosParsingStats> group = new ArrayList<>(entry.getValue());
            group.sort(Comparator.comparing(
                    s -> s.getStartTime() != null ? s.getStartTime() : ZonedDateTime.now(),
                    Comparator.naturalOrder()));
            String status = checkService.evaluateGroup(group, dropThreshold, errorGrowthThreshold);
            siteCityStatuses.put(entry.getKey(), status);
            if (!"OK".equals(status)) {
                buildGroupIssues(group, status, dropThreshold, errorGrowthThreshold, issues);
            }
        }

        // Ожидаемые сайты — проверяем ненайденные
        List<ZoomosCityId> allCityIds = parserService.getCityIds(run.getShop().getId())
                .stream().filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .collect(Collectors.toList());
        Set<String> sitesWithData = stats.stream()
                .map(ZoomosParsingStats::getSiteName)
                .collect(Collectors.toSet());
        for (ZoomosCityId cid : allCityIds) {
            if (!sitesWithData.contains(cid.getSiteName())) {
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("site", cid.getSiteName());
                issue.put("city", cid.getCityIds());
                issue.put("type", "NOT_FOUND");
                issue.put("message", "Нет данных за указанный период");
                issues.add(issue);
            }
        }

        // Группируем по сайту (верхний уровень)
        // Внутри каждой группы — по городу (для правильного отображения стрелок динамики)
        Map<String, List<ZoomosParsingStats>> bySite = stats.stream()
                .collect(Collectors.groupingBy(ZoomosParsingStats::getSiteName));

        List<Map<String, Object>> groups = new ArrayList<>();

        bySite.forEach((siteName, siteStats) -> {
            // Худший статус по всем city в этом сайте
            String worstStatus = siteStats.stream()
                    .map(s -> siteCityStatuses.getOrDefault(
                            s.getSiteName() + "|" + (s.getCityName() != null ? s.getCityName() : ""), "OK"))
                    .min(Comparator.comparingInt(st -> "ERROR".equals(st) ? 0 : "WARNING".equals(st) ? 1 : 2))
                    .orElse("OK");

            String checkType = siteStats.get(0).getCheckType();

            // Подгруппы по городу, каждая отсортирована DESC по startTime
            // Сохраняем порядок: худшие города первыми
            Map<String, List<ZoomosParsingStats>> byCity = siteStats.stream()
                    .collect(Collectors.groupingBy(
                            s -> s.getCityName() != null ? s.getCityName() : "",
                            LinkedHashMap::new, Collectors.toList()));

            List<Map<String, Object>> cityGroups = new ArrayList<>();
            byCity.forEach((cityName, cityStats) -> {
                cityStats.sort(Comparator.comparing(
                        (ZoomosParsingStats s) -> s.getStartTime() != null ? s.getStartTime() : ZonedDateTime.now())
                        .reversed());
                String cityStatus = siteCityStatuses.getOrDefault(siteName + "|" + cityName, "OK");
                Map<String, Object> cg = new LinkedHashMap<>();
                cg.put("cityName", cityName.isEmpty() ? null : cityName);
                cg.put("status", cityStatus);
                cg.put("stats", cityStats);
                cityGroups.add(cg);
            });
            // Сортируем города: ERROR первыми
            cityGroups.sort(Comparator.comparingInt(cg ->
                    "ERROR".equals(cg.get("status")) ? 0 : "WARNING".equals(cg.get("status")) ? 1 : 2));

            Map<String, Object> g = new LinkedHashMap<>();
            g.put("siteName", siteName);
            g.put("checkType", checkType);
            g.put("status", worstStatus);
            g.put("count", siteStats.size());
            g.put("cityGroups", cityGroups);
            groups.add(g);
        });

        // Сортируем группы: ERROR первые, потом WARNING, потом OK, внутри — по имени сайта
        groups.sort(Comparator.comparingInt((Map<String, Object> g) ->
                "ERROR".equals(g.get("status")) ? 0 : "WARNING".equals(g.get("status")) ? 1 : 2)
                .thenComparing(g -> (String) g.get("siteName")));

        boolean canDeliver = issues.stream()
                .noneMatch(i -> "ERROR".equals(i.get("type")) || "NOT_FOUND".equals(i.get("type")));

        // Упрощённые данные для графиков (без JPA-объектов — только примитивы)
        List<Map<String, Object>> chartData = new ArrayList<>();
        for (Map<String, Object> g : groups) {
            Map<String, Object> cd = new LinkedHashMap<>();
            cd.put("siteName", g.get("siteName"));
            List<Map<String, Object>> cityCd = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cgList = (List<Map<String, Object>>) g.get("cityGroups");
            for (Map<String, Object> cg : cgList) {
                Map<String, Object> cityCdItem = new LinkedHashMap<>();
                cityCdItem.put("cityName", cg.get("cityName"));
                @SuppressWarnings("unchecked")
                List<ZoomosParsingStats> cityStats = (List<ZoomosParsingStats>) cg.get("stats");
                List<Map<String, Object>> statsCd = new ArrayList<>();
                for (ZoomosParsingStats s : cityStats) {
                    Map<String, Object> sc = new LinkedHashMap<>();
                    sc.put("startTime", s.getStartTime() != null ? s.getStartTime().toInstant().toEpochMilli() : null);
                    sc.put("totalProducts", s.getTotalProducts());
                    sc.put("inStock", s.getInStock());
                    sc.put("errorCount", s.getErrorCount());
                    statsCd.add(sc);
                }
                cityCdItem.put("stats", statsCd);
                cityCd.add(cityCdItem);
            }
            cd.put("cityGroups", cityCd);
            chartData.add(cd);
        }

        // Текст для ИТ: сайт — список городов (через запятую) — проблема
        // Группируем issues по сайту, собираем города с одинаковой проблемой
        Map<String, List<String>> itBySite = new LinkedHashMap<>();
        for (Map<String, Object> issue : issues) {
            String site = (String) issue.get("site");
            String city = (String) issue.get("city");
            String msg  = (String) issue.get("message");
            String type = (String) issue.get("type");
            String cityPart = (city != null && !city.isBlank()) ? city : null;
            String detail = (msg != null && !msg.isBlank()) ? msg : type;
            String line = (cityPart != null ? cityPart + " — " : "") + detail;
            itBySite.computeIfAbsent(site, k -> new ArrayList<>()).add(line);
        }
        StringBuilder itText = new StringBuilder();
        for (Map.Entry<String, List<String>> e : itBySite.entrySet()) {
            itText.append(e.getKey()).append(":\n");
            for (String line : e.getValue()) {
                itText.append("  ").append(line).append("\n");
            }
        }

        model.addAttribute("run", run);
        model.addAttribute("groups", groups);
        model.addAttribute("issues", issues);
        model.addAttribute("canDeliver", canDeliver);
        model.addAttribute("chartData", chartData);
        model.addAttribute("itText", itText.toString().trim());
        return "zoomos/check-results";
    }

    private void buildGroupIssues(List<ZoomosParsingStats> sortedAsc, String groupStatus,
                                    int dropThreshold, int errorGrowthThreshold,
                                    List<Map<String, Object>> issues) {
        if (sortedAsc.size() < 2) return;
        // Оцениваем только последнюю пару (текущее состояние)
        ZoomosParsingStats prev = sortedAsc.get(sortedAsc.size() - 2);
        ZoomosParsingStats newest = sortedAsc.get(sortedAsc.size() - 1);

        String siteName = newest.getSiteName();
        String cityName = newest.getCityName();
        boolean alwaysZeroStock = sortedAsc.stream()
                .allMatch(s -> s.getInStock() == null || s.getInStock() == 0);

        // ERROR только: падение "В наличии"
        if (!alwaysZeroStock) {
            Integer prevStock = prev.getInStock();
            Integer newStock = newest.getInStock();
            if (prevStock != null && prevStock > 0) {
                if (newStock != null && newStock == 0) {
                    Map<String, Object> issue = new LinkedHashMap<>();
                    issue.put("site", siteName);
                    issue.put("city", cityName);
                    issue.put("type", "ERROR");
                    issue.put("message", String.format("В наличии: %d → 0 (−100%%)", prevStock));
                    issues.add(issue);
                } else if (newStock != null) {
                    double drop = (double)(prevStock - newStock) / prevStock * 100;
                    if (drop > dropThreshold) {
                        Map<String, Object> issue = new LinkedHashMap<>();
                        issue.put("site", siteName);
                        issue.put("city", cityName);
                        issue.put("type", "ERROR");
                        issue.put("message", String.format("Падение 'В наличии': %d → %d (−%.0f%%)", prevStock, newStock, drop));
                        issues.add(issue);
                    }
                }
            }
        }

        // WARNING: рост ошибок
        int prevErr = prev.getErrorCount() != null ? prev.getErrorCount() : 0;
        int newErr = newest.getErrorCount() != null ? newest.getErrorCount() : 0;
        if (prevErr > 0 && newErr > prevErr) {
            double growth = (double)(newErr - prevErr) / prevErr * 100;
            if (growth > errorGrowthThreshold) {
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("site", siteName);
                issue.put("city", cityName);
                issue.put("type", "WARNING");
                issue.put("message", String.format("Рост ошибок: %d → %d (+%.0f%%)", prevErr, newErr, growth));
                issues.add(issue);
            }
        }

        // WARNING: падение товаров
        Integer prevTotal = prev.getTotalProducts();
        Integer newTotal = newest.getTotalProducts();
        if (prevTotal != null && newTotal != null && prevTotal > 0) {
            double drop = (double)(prevTotal - newTotal) / prevTotal * 100;
            if (drop > dropThreshold) {
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("site", siteName);
                issue.put("city", cityName);
                issue.put("type", "WARNING");
                issue.put("message", String.format("Падение товаров: %d → %d (−%.0f%%)", prevTotal, newTotal, drop));
                issues.add(issue);
            }
        }
    }

    @GetMapping("/check/latest")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getLatestRun(@RequestParam Long shopId) {
        List<ZoomosCheckRun> runs = checkRunRepository.findByShopIdOrderByStartedAtDesc(shopId);
        if (runs.isEmpty()) {
            return ResponseEntity.ok(Map.of("runId", 0));
        }
        ZoomosCheckRun run = runs.get(0);
        Map<String, Object> result = new HashMap<>();
        result.put("runId", run.getId());
        result.put("totalSites", run.getTotalSites());
        result.put("okCount", run.getOkCount());
        result.put("warningCount", run.getWarningCount());
        result.put("errorCount", run.getErrorCount());
        result.put("notFoundCount", run.getNotFoundCount());
        result.put("status", run.getStatus());
        result.put("dateFrom", run.getDateFrom() != null ? run.getDateFrom().toString() : null);
        result.put("dateTo", run.getDateTo() != null ? run.getDateTo().toString() : null);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/check/history")
    public String checkHistory(Model model) {
        List<ZoomosCheckRun> runs = checkRunRepository.findAll(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "startedAt"));
        model.addAttribute("runs", runs);
        return "zoomos/check-history";
    }
}
