package com.java.controller;

import com.java.config.ZoomosConfig;
import com.java.model.entity.*;
import com.java.repository.ZoomosCityIdRepository;
import com.java.repository.ZoomosCheckRunRepository;
import com.java.repository.ZoomosKnownSiteRepository;
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
import java.time.format.DateTimeFormatter;
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
    private final ZoomosKnownSiteRepository knownSiteRepository;
    private final ZoomosCityIdRepository cityIdRepository;
    private final ZoomosConfig zoomosConfig;

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

    @PostMapping("/shops/{shopName}/sync-from-matching")
    public String syncFromMatching(@PathVariable String shopName, RedirectAttributes ra) {
        try {
            String result = parserService.syncFromMatchingPage(shopName);
            ra.addFlashAttribute("success", result);
        } catch (Exception e) {
            log.error("Ошибка синхронизации из матчинга {}", shopName, e);
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

    @PostMapping("/city-ids/{id}/update-addresses")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateAddressIds(@PathVariable Long id,
                                                                 @RequestBody(required = false) Map<String, List<String>> addressMapping) {
        return cityIdRepository.findById(id).map(entry -> {
            if (addressMapping == null || addressMapping.isEmpty()) {
                entry.setAddressIds(null);
            } else {
                try {
                    entry.setAddressIds(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(addressMapping));
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(Map.<String, Object>of("success", false, "error", e.getMessage()));
                }
            }
            cityIdRepository.save(entry);
            return ResponseEntity.ok(Map.<String, Object>of("success", true));
        }).orElse(ResponseEntity.notFound().<Map<String, Object>>build());
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
            @RequestParam(required = false) String timeFrom,
            @RequestParam(required = false) String timeTo,
            @RequestParam(defaultValue = "10") int dropThreshold,
            @RequestParam(defaultValue = "30") int errorGrowthThreshold) {
        log.info("runCheck: shopId={} dateFrom='{}' dateTo='{}' timeFrom='{}' timeTo='{}'",
                shopId, dateFrom, dateTo, timeFrom, timeTo);
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
        final String tf = (timeFrom != null && !timeFrom.isBlank()) ? timeFrom : null;
        final String tt = (timeTo   != null && !timeTo.isBlank())   ? timeTo   : null;
        try {
            String operationId = UUID.randomUUID().toString();

            // Запускаем в фоне, чтобы не блокировать HTTP
            CompletableFuture.runAsync(() -> {
                try {
                    checkService.runCheck(shopId, from, to, tf, tt, dropThreshold, errorGrowthThreshold, operationId);
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

        List<ZoomosParsingStats> allStatsList = parsingStatsRepository
                .findByCheckRunIdOrderBySiteNameAscCityNameAsc(runId);

        // Разделяем на завершённые и in-progress
        List<ZoomosParsingStats> stats = allStatsList.stream()
                .filter(s -> !Boolean.FALSE.equals(s.getIsFinished()))
                .collect(Collectors.toCollection(ArrayList::new));
        List<ZoomosParsingStats> inProgressStats = allStatsList.stream()
                .filter(s -> Boolean.FALSE.equals(s.getIsFinished()))
                .collect(Collectors.toList());

        // Записи с completionPercent >= 100 фактически завершены — сервер просто не показывает
        // их на глобальной onlyFinished=1 странице когда внутренний % < 100.
        // Промоутируем в stats: появятся в деталях и участвуют в оценке динамики.
        inProgressStats.stream()
                .filter(ip -> ip.getCompletionPercent() != null && ip.getCompletionPercent() >= 100)
                .forEach(stats::add);
        inProgressStats = inProgressStats.stream()
                .filter(ip -> ip.getCompletionPercent() == null || ip.getCompletionPercent() < 100)
                .collect(Collectors.toList());

        int dropThreshold = run.getDropThreshold() != null ? run.getDropThreshold() : 10;
        int errorGrowthThreshold = run.getErrorGrowthThreshold() != null ? run.getErrorGrowthThreshold() : 30;

        // Группируем по site+city+address для оценки динамики.
        // Строки с addressId → отдельная группа внутри города (address-level проверка).
        Map<String, List<ZoomosParsingStats>> bySiteCity = stats.stream()
                .collect(Collectors.groupingBy(s -> {
                    String key = s.getSiteName() + "|" + (s.getCityName() != null ? s.getCityName() : "");
                    if (s.getAddressId() != null && !s.getAddressId().isBlank()) {
                        key += "|" + s.getAddressId();
                    }
                    return key;
                }));

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
                buildGroupIssues(group, status, dropThreshold, errorGrowthThreshold, issues, run.getShop().getShopName());
            }
        }

        // Ожидаемые города/адреса — проверяем ненайденные на уровне каждого города и адреса
        List<ZoomosCityId> allCityIds = parserService.getCityIds(run.getShop().getId())
                .stream().filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .collect(Collectors.toList());

        // Множества найденных городов и адресов из реальных данных
        Set<String> foundCityKeys = new HashSet<>();    // "siteName|cityId"
        Set<String> foundAddressKeys = new HashSet<>(); // "siteName|addressId"
        Map<String, String> addressToCityFromData = new HashMap<>(); // "siteName|addressId" → cityId
        for (ZoomosParsingStats s : stats) {
            String cId = ZoomosCheckService.extractCityId(s.getCityName());
            if (cId != null) foundCityKeys.add(s.getSiteName() + "|" + cId);
            if (s.getAddressId() != null && !s.getAddressId().isBlank()) {
                foundAddressKeys.add(s.getSiteName() + "|" + s.getAddressId());
                if (cId != null) addressToCityFromData.put(s.getSiteName() + "|" + s.getAddressId(), cId);
            }
        }
        // In-progress данные — индексируем по site+city и site+address
        Map<String, ZoomosParsingStats> inProgressByCityKey = new HashMap<>();
        for (ZoomosParsingStats ip : inProgressStats) {
            String cId = ZoomosCheckService.extractCityId(ip.getCityName());
            if (cId != null) {
                String key = ip.getSiteName() + "|" + cId;
                inProgressByCityKey.merge(key, ip, (a, b) ->
                        a.getStartTime() != null && b.getStartTime() != null
                                && a.getStartTime().isAfter(b.getStartTime()) ? a : b);
            }
        }

        // Также индексируем in-progress по site+address
        Map<String, ZoomosParsingStats> inProgressByAddrKey = new HashMap<>();
        for (ZoomosParsingStats ip : inProgressStats) {
            if (ip.getAddressId() != null && !ip.getAddressId().isBlank()) {
                String key = ip.getSiteName() + "|" + ip.getAddressId();
                inProgressByAddrKey.merge(key, ip, (a, b) ->
                        a.getStartTime() != null && b.getStartTime() != null
                                && a.getStartTime().isAfter(b.getStartTime()) ? a : b);
            }
        }

        for (ZoomosCityId cid : allCityIds) {
            String site = cid.getSiteName();
            Set<String> expectedCities = ZoomosCheckService.parseCommaSeparated(cid.getCityIds());
            Map<String, Set<String>> addrMapping = ZoomosCheckService.parseAddressMapping(cid.getAddressIds());

            // Для плоского формата (key="") определяем город из данных парсинга
            // Строим полный маппинг addressId → cityId с учётом конфигурации и данных
            Set<String> addressCoveredCities = new HashSet<>();
            Map<String, String> addrToCityResolved = new HashMap<>(); // aid → cityId

            for (Map.Entry<String, Set<String>> addrEntry : addrMapping.entrySet()) {
                String mappedCity = addrEntry.getKey();
                for (String aid : addrEntry.getValue()) {
                    String resolvedCity;
                    if (!mappedCity.isEmpty()) {
                        resolvedCity = mappedCity;
                    } else {
                        // Плоский формат — ищем город из данных парсинга
                        resolvedCity = addressToCityFromData.get(site + "|" + aid);
                    }
                    if (resolvedCity != null) {
                        addrToCityResolved.put(aid, resolvedCity);
                        addressCoveredCities.add(resolvedCity);
                    }
                }
            }

            // Проверяем каждый ожидаемый адрес ИНДИВИДУАЛЬНО
            Set<String> allExpectedAddresses = ZoomosCheckService.flattenAddressIds(addrMapping);
            for (String aid : allExpectedAddresses) {
                if (foundAddressKeys.contains(site + "|" + aid)) continue; // данные есть — ОК

                String addrCity = addrToCityResolved.get(aid);
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("site", site);
                issue.put("city", addrCity != null ? addrCity : "");
                issue.put("cityId", addrCity != null ? addrCity : "");
                issue.put("addressId", aid);
                issue.put("checkType", cid.getCheckType());
                issue.put("shopName", run.getShop().getShopName());

                // Ищем in-progress сначала по адресу, потом по городу
                ZoomosParsingStats ip = inProgressByAddrKey.get(site + "|" + aid);
                if (ip == null && addrCity != null) {
                    ip = inProgressByCityKey.get(site + "|" + addrCity);
                }
                addIssueStatus(issue, ip);
                issues.add(issue);
            }

            // Проверяем города без покрытия адресами
            for (String cityId : expectedCities) {
                if (addressCoveredCities.contains(cityId)) continue;
                if (foundCityKeys.contains(site + "|" + cityId)) continue;

                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("site", site);
                issue.put("city", cityId);
                issue.put("cityId", cityId);
                issue.put("checkType", cid.getCheckType());
                issue.put("shopName", run.getShop().getShopName());

                ZoomosParsingStats ip = inProgressByCityKey.get(site + "|" + cityId);
                addIssueStatus(issue, ip);
                issues.add(issue);
            }
        }

        // Группируем по сайту (верхний уровень)
        // Внутри каждой группы — по городу (для правильного отображения стрелок динамики)
        Map<String, List<ZoomosParsingStats>> bySite = stats.stream()
                .collect(Collectors.groupingBy(ZoomosParsingStats::getSiteName));

        List<Map<String, Object>> groups = new ArrayList<>();

        bySite.forEach((siteName, siteStats) -> {
            // Худший статус по всем city в этом сайте (с учётом addressId в ключе)
            String worstStatus = siteStats.stream()
                    .map(s -> {
                        String key = s.getSiteName() + "|" + (s.getCityName() != null ? s.getCityName() : "");
                        if (s.getAddressId() != null && !s.getAddressId().isBlank()) key += "|" + s.getAddressId();
                        return siteCityStatuses.getOrDefault(key, "OK");
                    })
                    .min(Comparator.comparingInt(ZoomosAnalysisController::statusPriority))
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

                // Подгруппируем по addressId внутри города
                // "" — строки без адреса (city-level), остальные — отдельные адреса
                Map<String, List<ZoomosParsingStats>> byAddress = new LinkedHashMap<>();
                for (ZoomosParsingStats s : cityStats) {
                    String addrKey = s.getAddressId() != null && !s.getAddressId().isBlank()
                            ? s.getAddressId() : "";
                    byAddress.computeIfAbsent(addrKey, k -> new ArrayList<>()).add(s);
                }

                List<Map<String, Object>> addressGroups = new ArrayList<>();
                byAddress.forEach((addrId, addrStats) -> {
                    addrStats.sort(Comparator.comparing(
                            (ZoomosParsingStats s) -> s.getStartTime() != null ? s.getStartTime() : ZonedDateTime.now())
                            .reversed());
                    String addrStatusKey = siteName + "|" + cityName
                            + (addrId.isEmpty() ? "" : "|" + addrId);
                    String addrStatus = siteCityStatuses.getOrDefault(addrStatusKey, "OK");
                    // Имя адреса из первой строки с addressName
                    String addrName = addrStats.stream()
                            .filter(s -> s.getAddressName() != null)
                            .findFirst().map(ZoomosParsingStats::getAddressName).orElse(null);
                    Map<String, Object> ag = new LinkedHashMap<>();
                    ag.put("addressId", addrId.isEmpty() ? null : addrId);
                    ag.put("addressName", addrName);
                    ag.put("status", addrStatus);
                    ag.put("stats", addrStats);
                    addressGroups.add(ag);
                });
                // Сортируем адресные группы: ERROR первыми
                addressGroups.sort(Comparator.comparingInt(ag -> statusPriority((String) ag.get("status"))));

                // Если в городе есть хотя бы один адрес с 100% завершением → город OK
                boolean anyFullyComplete = cityStats.stream()
                        .anyMatch(s -> Boolean.TRUE.equals(s.getIsFinished())
                                && s.getCompletionPercent() != null && s.getCompletionPercent() == 100
                                && s.getAddressId() != null);
                String cityStatus = anyFullyComplete ? "OK"
                        : addressGroups.stream()
                                .map(ag -> (String) ag.get("status"))
                                .min(Comparator.comparingInt(ZoomosAnalysisController::statusPriority))
                                .orElse(siteCityStatuses.getOrDefault(siteName + "|" + cityName, "OK"));

                Map<String, Object> cg = new LinkedHashMap<>();
                cg.put("cityName", cityName.isEmpty() ? null : cityName);
                cg.put("status", cityStatus);
                cg.put("stats", cityStats);
                cg.put("addressGroups", addressGroups);
                cityGroups.add(cg);
            });
            // Сортируем города: ERROR первыми
            cityGroups.sort(Comparator.comparingInt(cg -> statusPriority((String) cg.get("status"))));

            Map<String, Object> g = new LinkedHashMap<>();
            g.put("siteName", siteName);
            g.put("checkType", checkType);
            g.put("status", worstStatus);
            g.put("count", siteStats.size());
            g.put("cityGroups", cityGroups);
            groups.add(g);
        });

        // Сортируем группы: ERROR первые, потом WARNING, потом OK, внутри — по имени сайта
        groups.sort(Comparator.comparingInt((Map<String, Object> g) -> statusPriority((String) g.get("status")))
                .thenComparing(g -> (String) g.get("siteName")));

        // Добавляем NOT_FOUND/IN_PROGRESS пары в groups для отображения в Блоке 3
        Map<String, Map<String, Object>> groupBySite = new LinkedHashMap<>();
        for (Map<String, Object> g : groups) {
            groupBySite.put((String) g.get("siteName"), g);
        }
        for (Map<String, Object> issue : issues) {
            String iType = (String) issue.get("type");
            if (!"NOT_FOUND".equals(iType) && !"IN_PROGRESS".equals(iType)) continue;
            String iSite      = (String) issue.get("site");
            String iCity      = (String) issue.get("city");
            String iCityId    = (String) issue.get("cityId");
            String iAddrId    = (String) issue.get("addressId");
            String iAddrName  = (String) issue.get("addressName");
            String iMsg       = (String) issue.get("message");
            String iCheckType = (String) issue.get("checkType");

            // Найти или создать site group
            Map<String, Object> siteGroup = groupBySite.computeIfAbsent(iSite, k -> {
                Map<String, Object> ng = new LinkedHashMap<>();
                ng.put("siteName", iSite);
                ng.put("checkType", iCheckType);
                ng.put("status", iType);
                ng.put("count", 0);
                ng.put("cityGroups", new ArrayList<>());
                groups.add(ng);
                return ng;
            });

            // Найти или создать city group по cityId
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> siteGroupCityGroups = (List<Map<String, Object>>) siteGroup.get("cityGroups");
            Map<String, Object> cityGroup = siteGroupCityGroups.stream()
                    .filter(cg -> {
                        String cgCityName = (String) cg.get("cityName");
                        String cgCityId = ZoomosCheckService.extractCityId(cgCityName);
                        return iCityId.equals(cgCityId) || iCityId.equals(cgCityName) || iCity.equals(cgCityName);
                    })
                    .findFirst()
                    .orElseGet(() -> {
                        Map<String, Object> ncg = new LinkedHashMap<>();
                        ncg.put("cityName", iCity);
                        ncg.put("status", iType);
                        ncg.put("stats", new ArrayList<>());
                        ncg.put("addressGroups", new ArrayList<>());
                        siteGroupCityGroups.add(ncg);
                        return ncg;
                    });

            // Добавить address group с пустыми stats и issueMessage (если нет дубля)
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cityGroupAddrGroups = (List<Map<String, Object>>) cityGroup.get("addressGroups");
            boolean alreadyExists = cityGroupAddrGroups.stream().anyMatch(ag ->
                    iAddrId != null ? iAddrId.equals(ag.get("addressId")) : ag.get("addressId") == null);
            if (!alreadyExists) {
                Map<String, Object> ag = new LinkedHashMap<>();
                ag.put("addressId", iAddrId);
                ag.put("addressName", iAddrName);
                ag.put("status", iType);
                ag.put("stats", new ArrayList<>());
                ag.put("issueMessage", iMsg);
                ag.put("inProgressStat", issue.get("inProgress"));
                cityGroupAddrGroups.add(ag);
            }
        }

        // Пересортировать все уровни с учётом новых записей
        for (Map<String, Object> g : groups) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cgs = (List<Map<String, Object>>) g.get("cityGroups");
            for (Map<String, Object> cg : cgs) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> ags = (List<Map<String, Object>>) cg.get("addressGroups");
                ags.sort(Comparator.comparingInt(ag -> statusPriority((String) ag.get("status"))));
                String worstCityStatus = ags.stream().map(ag -> (String) ag.get("status"))
                        .min(Comparator.comparingInt(ZoomosAnalysisController::statusPriority))
                        .orElse((String) cg.get("status"));
                cg.put("status", worstCityStatus);
            }
            cgs.sort(Comparator.comparingInt(cg -> statusPriority((String) cg.get("status"))));
            String worstSiteStatus = cgs.stream().map(cg -> (String) cg.get("status"))
                    .min(Comparator.comparingInt(ZoomosAnalysisController::statusPriority))
                    .orElse((String) g.get("status"));
            g.put("status", worstSiteStatus);
        }
        groups.sort(Comparator.comparingInt((Map<String, Object> g) -> statusPriority((String) g.get("status")))
                .thenComparing(g -> (String) g.get("siteName")));

        boolean canDeliver = issues.stream()
                .noneMatch(i -> "ERROR".equals(i.get("type"))
                        || "NOT_FOUND".equals(i.get("type"))
                        || "IN_PROGRESS".equals(i.get("type")));

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

        // Текст для ИТ: сайт — список городов (через запятую) — проблема + ссылка на историю
        DateTimeFormatter itDateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String dateFromStr = run.getDateFrom().format(itDateFmt);
        String dateToStr   = run.getDateTo().format(itDateFmt);
        long ts = System.currentTimeMillis();

        Map<String, List<String>> itBySite = new LinkedHashMap<>();
        for (Map<String, Object> issue : issues) {
            String site        = (String) issue.get("site");
            String city        = (String) issue.get("city");
            String msg         = (String) issue.get("message");
            String type        = (String) issue.get("type");
            String checkType   = (String) issue.get("checkType");
            String shopName    = (String) issue.get("shopName");
            String cityId      = (String) issue.get("cityId");
            String addressId   = (String) issue.get("addressId");
            String addressName = (String) issue.get("addressName");
            if (cityId == null) cityId = "";

            // Заголовок строки: "cityId — addressId (если есть) — сообщение"
            // ВАЖНО: cityId и addressId — одиночные значения (не массивы)
            String addrParam = (addressId != null && !addressId.isBlank()) ? addressId : "";
            String historyUrl = zoomosConfig.getBaseUrl()
                    + "/shops-parser/" + site + "/parsing-history"
                    + "?upd=" + ts
                    + "&dateFrom=" + dateFromStr
                    + "&dateTo=" + dateToStr
                    + "&launchDate=&shop=" + ("API".equals(checkType) ? "-" : shopName)
                    + "&site=&cityId=" + cityId + "&addressId=" + addrParam + "&accountId=&server=";
            String cityPart = (city != null && !city.isBlank()) ? city : null;
            String addrPart = addrParam.isBlank() ? "" : ", адрес " + addrParam;
            String detail = (msg != null && !msg.isBlank()) ? msg : type;
            String line = (cityPart != null ? cityPart + addrPart + " — " : "") + detail
                    + "\n    История: " + historyUrl;
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
        model.addAttribute("baseUrl", zoomosConfig.getBaseUrl());
        return "zoomos/check-results";
    }

    private void buildGroupIssues(List<ZoomosParsingStats> sortedAsc, String groupStatus,
                                    int dropThreshold, int errorGrowthThreshold,
                                    List<Map<String, Object>> issues, String shopName) {
        if (sortedAsc.size() < 2) return;
        // Оцениваем только последнюю пару (текущее состояние)
        ZoomosParsingStats prev = sortedAsc.get(sortedAsc.size() - 2);
        ZoomosParsingStats newest = sortedAsc.get(sortedAsc.size() - 1);

        String siteName   = newest.getSiteName();
        String cityName   = newest.getCityName();
        String checkType  = newest.getCheckType();
        String addressId  = newest.getAddressId();
        String addressName = newest.getAddressName();
        // Извлекаем числовой ID города из "3509 - Вологда"
        String cityId = cityName != null && cityName.contains(" - ")
                ? cityName.substring(0, cityName.indexOf(" - ")).trim()
                : (cityName != null ? cityName.trim() : "");
        boolean alwaysZeroStock = sortedAsc.stream()
                .allMatch(s -> s.getInStock() == null || s.getInStock() == 0);

        // ERROR только: падение "В наличии"
        if (!alwaysZeroStock) {
            Integer prevStock = prev.getInStock();
            Integer newStock = newest.getInStock();
            if (prevStock != null && prevStock > 0) {
                if (newStock != null && newStock == 0) {
                    Map<String, Object> issue = new LinkedHashMap<>();
                    issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
                    issue.put("addressId", addressId); issue.put("addressName", addressName);
                    issue.put("checkType", checkType); issue.put("shopName", shopName);
                    issue.put("type", "ERROR");
                    issue.put("message", String.format("В наличии: %d → 0 (−100%%)", prevStock));
                    issues.add(issue);
                } else if (newStock != null) {
                    double drop = (double)(prevStock - newStock) / prevStock * 100;
                    if (drop > dropThreshold) {
                        Map<String, Object> issue = new LinkedHashMap<>();
                        issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
                        issue.put("addressId", addressId); issue.put("addressName", addressName);
                        issue.put("checkType", checkType); issue.put("shopName", shopName);
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
                issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
                issue.put("addressId", addressId); issue.put("addressName", addressName);
                issue.put("checkType", checkType); issue.put("shopName", shopName);
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
                issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
                issue.put("addressId", addressId); issue.put("addressName", addressName);
                issue.put("checkType", checkType); issue.put("shopName", shopName);
                issue.put("type", "WARNING");
                issue.put("message", String.format("Падение товаров: %d → %d (−%.0f%%)", prevTotal, newTotal, drop));
                issues.add(issue);
            }
        }
    }

    /**
     * Устанавливает тип и сообщение для issue на основе in-progress статистики.
     * IN_PROGRESS: показывает старт, процент выполнения и время обновления.
     * NOT_FOUND:   данных нет совсем.
     */
    private void addIssueStatus(Map<String, Object> issue, ZoomosParsingStats ip) {
        if (ip == null) {
            issue.put("type", "NOT_FOUND");
            String aid = (String) issue.get("addressId");
            String cityId = (String) issue.get("cityId");
            if (aid != null && !aid.isBlank()) {
                issue.put("message", "Нет данных по адресу " + aid + " за указанный период");
            } else {
                issue.put("message", "Нет данных по городу " + (cityId != null ? cityId : "") + " за указанный период");
            }
        } else {
            boolean frozen = ip.getUpdatedTime() != null &&
                    ip.getUpdatedTime().isBefore(ZonedDateTime.now().minusHours(2));
            issue.put("type", frozen ? "NOT_FOUND" : "IN_PROGRESS");
            issue.put("inProgress", ip);
            String pct = ip.getCompletionTotal() != null ? ip.getCompletionTotal() : "?";
            String startStr = ip.getStartTime() != null
                    ? ip.getStartTime().format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
                    : "?";
            String updStr = ip.getUpdatedTime() != null
                    ? ip.getUpdatedTime().format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
                    : null;
            if (frozen) {
                issue.put("message", "Выкачка зависла: старт " + startStr + ", выполнено " + pct
                        + (updStr != null ? ", обновл. " + updStr : ""));
            } else {
                issue.put("message", "Выкачка в процессе: старт " + startStr + ", выполнено " + pct
                        + (updStr != null ? ", обновл. " + updStr : ""));
            }
        }
    }

    private static int statusPriority(String st) {
        return switch (st != null ? st : "") {
            case "ERROR", "NOT_FOUND" -> 0;
            case "IN_PROGRESS" -> 1;
            case "WARNING" -> 2;
            default -> 3; // OK
        };
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

    // =========================================================================
    // Справочник сайтов /zoomos/sites
    // =========================================================================

    @GetMapping("/sites")
    public String showSites(Model model) {
        model.addAttribute("sites", knownSiteRepository.findAllByOrderBySiteNameAsc());
        return "zoomos/sites";
    }

    @PostMapping("/sites/add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addSites(@RequestParam String siteNames,
                                                         @RequestParam(defaultValue = "ITEM") String checkType) {
        if (!"API".equals(checkType) && !"ITEM".equals(checkType)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Недопустимый тип: " + checkType));
        }
        List<String> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String raw : siteNames.split(",")) {
            String name = raw.trim().toLowerCase();
            if (name.isEmpty()) continue;
            if (knownSiteRepository.existsBySiteName(name)) {
                skipped.add(name);
            } else {
                knownSiteRepository.save(ZoomosKnownSite.builder()
                        .siteName(name).checkType(checkType).build());
                added.add(name);
            }
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "added", added,
                "skipped", skipped
        ));
    }

    @PostMapping("/sites/{id}/check-type")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateKnownSiteCheckType(@PathVariable Long id,
                                                                          @RequestParam String checkType) {
        if (!"API".equals(checkType) && !"ITEM".equals(checkType)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Недопустимый тип: " + checkType));
        }
        return knownSiteRepository.findById(id).map(site -> {
            site.setCheckType(checkType);
            knownSiteRepository.save(site);
            // Каскад: обновляем все city_ids с таким же siteName
            List<ZoomosCityId> cityIds = cityIdRepository.findAllBySiteName(site.getSiteName());
            cityIds.forEach(c -> c.setCheckType(checkType));
            if (!cityIds.isEmpty()) cityIdRepository.saveAll(cityIds);
            return ResponseEntity.ok(Map.<String, Object>of("success", true, "updatedCityIds", cityIds.size()));
        }).orElse(ResponseEntity.badRequest().body(Map.of("success", false, "error", "Сайт не найден")));
    }

    @PostMapping("/sites/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteKnownSite(@PathVariable Long id) {
        if (!knownSiteRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Сайт не найден"));
        }
        knownSiteRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
