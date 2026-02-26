package com.java.controller;

import com.java.config.ZoomosConfig;
import com.java.model.entity.*;
import com.java.repository.ZoomosCityAddressRepository;
import com.java.repository.ZoomosCityIdRepository;
import com.java.repository.ZoomosCityNameRepository;
import com.java.repository.ZoomosCheckRunRepository;
import com.java.repository.ZoomosKnownSiteRepository;
import com.java.repository.ZoomosParserPatternRepository;
import com.java.repository.ZoomosParsingStatsRepository;
import com.java.repository.ZoomosShopRepository;
import com.java.repository.ZoomosShopScheduleRepository;
import com.java.service.ZoomosCheckService;
import com.java.service.ZoomosParserService;
import com.java.service.ZoomosSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final ZoomosCityNameRepository cityNameRepository;
    private final ZoomosCityAddressRepository cityAddressRepository;
    private final ZoomosConfig zoomosConfig;
    private final ZoomosShopRepository shopRepository;
    private final ZoomosShopScheduleRepository scheduleRepository;
    private final ZoomosSchedulerService schedulerService;
    private final ZoomosParserPatternRepository parserPatternRepository;

    @GetMapping({"", "/"})
    public String index(Model model) {
        List<ZoomosShop> allShops = parserService.getAllShops();
        List<ZoomosShop> shops = allShops.stream().filter(ZoomosShop::isEnabled).collect(Collectors.toList());
        List<ZoomosShop> disabledShops = allShops.stream().filter(s -> !s.isEnabled()).collect(Collectors.toList());

        Map<Long, List<ZoomosCityId>> cityIdsMap = new java.util.LinkedHashMap<>();
        for (ZoomosShop shop : allShops) {
            cityIdsMap.put(shop.getId(), parserService.getCityIds(shop.getId()));
        }
        Map<String, String> cityNamesMap = cityNameRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ZoomosCityName::getCityId, ZoomosCityName::getCityName));

        // Расписания для badge'ей вкл/выкл на каждой карточке
        Map<Long, ZoomosShopSchedule> schedulesMap = new java.util.LinkedHashMap<>();
        for (ZoomosShop shop : allShops) {
            scheduleRepository.findByShopId(shop.getId()).ifPresent(s -> schedulesMap.put(shop.getId(), s));
        }

        // Приоритетные сайты для иконок в таблице сайтов клиента
        Set<String> prioritySiteNames = knownSiteRepository.findAllByIsPriorityTrue().stream()
                .map(ZoomosKnownSite::getSiteName).collect(Collectors.toSet());

        model.addAttribute("shops", shops);
        model.addAttribute("disabledShops", disabledShops);
        model.addAttribute("cityIdsMap", cityIdsMap);
        model.addAttribute("cityNamesMap", cityNamesMap);
        model.addAttribute("schedulesMap", schedulesMap);
        model.addAttribute("prioritySiteNames", prioritySiteNames);
        model.addAttribute("baseUrl", zoomosConfig.getBaseUrl());
        return "zoomos/index";
    }

    /** AJAX-toggle расписания (для index.html без перезагрузки) */
    @PostMapping("/api/schedule/{shopId}/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleScheduleAjax(@PathVariable Long shopId) {
        schedulerService.toggleEnabled(shopId);
        boolean isEnabled = scheduleRepository.findByShopId(shopId)
                .map(ZoomosShopSchedule::isEnabled).orElse(false);
        return ResponseEntity.ok(Map.of("success", true, "isEnabled", isEnabled));
    }

    // =========================================================================
    // Справочник городов
    // =========================================================================

    @GetMapping("/city-names")
    public String cityNamesPage(Model model) {
        model.addAttribute("cityNames", cityNameRepository.findAll()
                .stream().sorted(java.util.Comparator.comparing(ZoomosCityName::getCityId))
                .toList());
        // Количество адресов по каждому городу
        Map<String, Long> addrCounts = new java.util.LinkedHashMap<>();
        cityAddressRepository.countByCityId()
                .forEach(row -> addrCounts.put((String) row[0], (Long) row[1]));
        model.addAttribute("addrCounts", addrCounts);
        return "zoomos/city-names";
    }

    /** Адреса из справочника для списка cityIds (через запятую) или всех */
    @GetMapping("/city-addresses")
    @ResponseBody
    public ResponseEntity<?> getCityAddresses(
            @RequestParam(required = false) String cityIds) {
        List<com.java.model.entity.ZoomosCityAddress> addrs;
        if (cityIds != null && !cityIds.isBlank()) {
            List<String> ids = Arrays.asList(cityIds.split(","));
            addrs = cityAddressRepository.findByCityIdInOrderByCityIdAscAddressIdAsc(ids);
        } else {
            addrs = cityAddressRepository.findAll();
        }
        Map<String, List<Map<String, String>>> result = new java.util.LinkedHashMap<>();
        for (com.java.model.entity.ZoomosCityAddress a : addrs) {
            result.computeIfAbsent(a.getCityId(), k -> new java.util.ArrayList<>())
                    .add(Map.of("id", a.getAddressId(),
                                "name", a.getAddressName() != null ? a.getAddressName() : ""));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/city-addresses/save")
    @ResponseBody
    public ResponseEntity<?> saveCityAddress(@RequestParam String cityId,
                                             @RequestParam String addressId,
                                             @RequestParam(required = false) String addressName) {
        if (cityId.isBlank() || addressId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Поля не могут быть пустыми"));
        }
        cityAddressRepository.upsert(cityId.trim(), addressId.trim(),
                addressName != null ? addressName.trim() : null);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/city-addresses/delete")
    @ResponseBody
    public ResponseEntity<?> deleteCityAddress(@RequestParam String cityId,
                                               @RequestParam String addressId) {
        cityAddressRepository.findByCityIdOrderByAddressId(cityId).stream()
                .filter(a -> a.getAddressId().equals(addressId))
                .findFirst()
                .ifPresent(a -> cityAddressRepository.deleteById(a.getId()));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/city-names/save")
    @ResponseBody
    public ResponseEntity<?> saveCityName(@RequestParam String cityId,
                                          @RequestParam String cityName) {
        if (cityId == null || cityId.isBlank() || cityName == null || cityName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Поля не могут быть пустыми"));
        }
        cityNameRepository.upsert(cityId.trim(), cityName.trim());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/city-names/{cityId}/delete")
    @ResponseBody
    public ResponseEntity<?> deleteCityName(@PathVariable String cityId) {
        cityNameRepository.deleteById(cityId);
        return ResponseEntity.ok(Map.of("success", true));
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
    // AJAX: preview/apply синхронизации
    // =========================================================================

    @GetMapping("/shops/{shopName}/sync-from-matching/preview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> previewSyncFromMatching(@PathVariable String shopName) {
        try {
            Map<String, Object> preview = parserService.previewSyncFromMatching(shopName);
            preview = new java.util.LinkedHashMap<>(preview);
            preview.put("success", true);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            log.error("Ошибка preview из матчинга {}", shopName, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/shops/{shopName}/sync-from-matching/apply")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applySyncFromMatching(
            @PathVariable String shopName,
            @RequestBody Map<String, List<String>> body) {
        try {
            List<String> toAdd = body.getOrDefault("toAdd", List.of());
            List<String> toDelete = body.getOrDefault("toDelete", List.of());
            String result = parserService.applySyncFromMatching(shopName, toAdd, toDelete);
            return ResponseEntity.ok(Map.of("success", true, "message", result));
        } catch (Exception e) {
            log.error("Ошибка apply из матчинга {}", shopName, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/shops/{shopName}/sync/preview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> previewSyncSettings(@PathVariable String shopName) {
        try {
            Map<String, Object> preview = parserService.previewSyncSettings(shopName);
            preview = new java.util.LinkedHashMap<>(preview);
            preview.put("success", true);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            log.error("Ошибка preview настроек {}", shopName, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/shops/{shopName}/sync/apply")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applySyncSettings(
            @PathVariable String shopName,
            @RequestBody Map<Long, String> entryIdToCityIds) {
        try {
            String result = parserService.applySyncSettings(shopName, entryIdToCityIds);
            return ResponseEntity.ok(Map.of("success", true, "message", result));
        } catch (Exception e) {
            log.error("Ошибка apply настроек {}", shopName, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/city-ids/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteCityId(@PathVariable Long id) {
        try {
            parserService.deleteCityId(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Ошибка удаления city-id {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/shops/{shopId}/city-ids/delete-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteAllCityIds(@PathVariable Long shopId) {
        try {
            cityIdRepository.deleteByShopId(shopId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Ошибка удаления всех city-ids для shopId={}", shopId, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/shops/{shopId}/toggle-enabled")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleShopEnabled(@PathVariable Long shopId) {
        try {
            ZoomosShop shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new IllegalArgumentException("Магазин не найден: " + shopId));
            shop.setEnabled(!shop.isEnabled());
            shopRepository.save(shop);
            return ResponseEntity.ok(Map.of("success", true, "isEnabled", shop.isEnabled()));
        } catch (Exception e) {
            log.error("Ошибка переключения enabled для shopId={}", shopId, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
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

    @PostMapping("/city-ids/{id}/parser-filter")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateParserFilter(
            @PathVariable Long id,
            @RequestParam(required = false) String include,
            @RequestParam(required = false, defaultValue = "OR") String includeMode,
            @RequestParam(required = false) String exclude) {
        try {
            parserService.updateParserFilters(id, include, includeMode, exclude);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Ошибка обновления фильтра парсера для id={}", id, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/parser-patterns")
    @ResponseBody
    public ResponseEntity<List<String>> getParserPatterns(@RequestParam String siteName) {
        List<String> patterns = parserPatternRepository.findBySiteNameOrderByPatternAsc(siteName)
                .stream().map(ZoomosParserPattern::getPattern).collect(Collectors.toList());
        return ResponseEntity.ok(patterns);
    }

    // =========================================================================
    // Проверка выкачки
    // =========================================================================

    /** Редирект со старого URL /zoomos/check → /zoomos (убрана страница запуска) */
    @GetMapping("/check")
    public String checkRedirect() {
        return "redirect:/zoomos";
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
            @RequestParam(defaultValue = "30") int errorGrowthThreshold,
            @RequestParam(defaultValue = "7") int baselineDays,
            @RequestParam(defaultValue = "5") int minAbsoluteErrors,
            @RequestParam(defaultValue = "30") int trendDropThreshold,
            @RequestParam(defaultValue = "100") int trendErrorThreshold) {
        log.info("runCheck: shopId={} dateFrom='{}' dateTo='{}' timeFrom='{}' timeTo='{}' baselineDays={} minAbsoluteErrors={} trendDrop={} trendErr={}",
                shopId, dateFrom, dateTo, timeFrom, timeTo, baselineDays, minAbsoluteErrors, trendDropThreshold, trendErrorThreshold);
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
        final int bl   = Math.max(0, baselineDays);
        final int mae  = Math.max(0, minAbsoluteErrors);
        final int tdt  = Math.max(1, trendDropThreshold);
        final int tet  = Math.max(1, trendErrorThreshold);
        try {
            String operationId = UUID.randomUUID().toString();

            // Запускаем в фоне, чтобы не блокировать HTTP
            CompletableFuture.runAsync(() -> {
                try {
                    checkService.runCheck(shopId, from, to, tf, tt, dropThreshold, errorGrowthThreshold, bl, mae, tdt, tet, operationId);
                    // Обновляем lastRunAt в расписании (если оно существует)
                    scheduleRepository.findByShopId(shopId).ifPresent(schedule -> {
                        schedule.setLastRunAt(java.time.ZonedDateTime.now());
                        scheduleRepository.save(schedule);
                    });
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
                .findByCheckRunIdAndIsBaselineFalseOrderBySiteNameAscCityNameAsc(runId);

        // Разделяем на завершённые и in-progress
        List<ZoomosParsingStats> stats = allStatsList.stream()
                .filter(s -> !Boolean.FALSE.equals(s.getIsFinished()))
                .collect(Collectors.toCollection(ArrayList::new));
        List<ZoomosParsingStats> inProgressStats = allStatsList.stream()
                .filter(s -> Boolean.FALSE.equals(s.getIsFinished()))
                .collect(Collectors.toList());

        // Записи с completionPercent >= 100 фактически завершены — сервер просто не показывает
        // их на глобальной onlyFinished=1 странице когда внутренний % < 100.
        // Промоутируем только те, что попадают в эффективное окно проверки (дата+время).
        // Overnight-парсинги (старт до timeFrom или до dateFrom) НЕ промоутируем.
        LocalTime tFromTime = (run.getTimeFrom() != null && !run.getTimeFrom().isBlank())
                ? LocalTime.parse(run.getTimeFrom()) : LocalTime.MIDNIGHT;
        ZonedDateTime effectiveRangeStart = run.getDateFrom().atTime(tFromTime).atZone(ZoneOffset.UTC);
        LocalDate checkDateTo = run.getDateTo();
        inProgressStats.stream()
                .filter(ip -> ip.getCompletionPercent() != null && ip.getCompletionPercent() >= 100
                        && ip.getStartTime() != null
                        && !ip.getStartTime().isBefore(effectiveRangeStart)
                        && !ip.getStartTime().toLocalDate().isAfter(checkDateTo))
                .forEach(stats::add);
        // Оставляем в inProgressStats только реально незавершённые записи в пределах дат проверки.
        // 100% записи убираем (они промоутированы в stats выше).
        // Overnight-парсинги (старт до timeFrom) НЕ убираем из inProgressStats — пользователь
        // должен видеть "Сейчас идёт" для любой выкачки начавшейся в день проверки,
        // независимо от timeFrom. Поэтому сравниваем только по дате, без учёта времени.
        inProgressStats = inProgressStats.stream()
                .filter(ip -> (ip.getCompletionPercent() == null || ip.getCompletionPercent() < 100)
                        && (ip.getStartTime() == null
                            || !ip.getStartTime().toLocalDate().isBefore(run.getDateFrom())))
                .collect(Collectors.toList());

        int dropThreshold = run.getDropThreshold() != null ? run.getDropThreshold() : 10;
        int errorGrowthThreshold = run.getErrorGrowthThreshold() != null ? run.getErrorGrowthThreshold() : 30;
        int minAbsoluteErrors = run.getMinAbsoluteErrors() != null ? run.getMinAbsoluteErrors() : 5;

        Set<String> ignoreStockSites = knownSiteRepository.findAllByIgnoreStockTrue()
                .stream().map(ZoomosKnownSite::getSiteName).collect(Collectors.toSet());
        Set<String> prioritySiteNames = knownSiteRepository.findAllByIsPriorityTrue()
                .stream().map(ZoomosKnownSite::getSiteName).collect(Collectors.toSet());

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
            boolean ignoreStock = group.get(0).getSiteName() != null
                    && ignoreStockSites.contains(group.get(0).getSiteName());
            String status = checkService.evaluateGroup(group, dropThreshold, errorGrowthThreshold,
                    minAbsoluteErrors, ignoreStock);
            siteCityStatuses.put(entry.getKey(), status);
            if (!"OK".equals(status)) {
                buildGroupIssues(group, status, dropThreshold, errorGrowthThreshold,
                        minAbsoluteErrors, ignoreStock, issues, run.getShop().getShopName());
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
                ZoomosParsingStats lastKnownAddr = (ip == null && addrCity != null)
                        ? parsingStatsRepository.findLatestFinishedBySiteAndCityId(site, addrCity).orElse(null)
                        : null;
                addIssueStatus(issue, ip, lastKnownAddr);
                if (ip == null && addrCity != null) {
                    parsingStatsRepository.findLatestInProgressBySiteAndCityId(site, addrCity)
                            .filter(curIp -> curIp.getCompletionPercent() == null || curIp.getCompletionPercent() < 100)
                            .ifPresent(curIp -> putCurrentInProgress(issue, curIp));
                }
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
                ZoomosParsingStats lastKnownCity = (ip == null)
                        ? parsingStatsRepository.findLatestFinishedBySiteAndCityId(site, cityId).orElse(null)
                        : null;
                addIssueStatus(issue, ip, lastKnownCity);
                if (ip == null) {
                    parsingStatsRepository.findLatestInProgressBySiteAndCityId(site, cityId)
                            .filter(curIp -> curIp.getCompletionPercent() == null || curIp.getCompletionPercent() < 100)
                            .ifPresent(curIp -> putCurrentInProgress(issue, curIp));
                }
                issues.add(issue);
            }
        }

        // Сайты без настроенных городов/адресов (глобальные выкачки без city_ids):
        // генерируем IN_PROGRESS/NOT_FOUND issue по факту наличия данных
        final List<ZoomosParsingStats> inProgressStatsFinal = inProgressStats;
        for (ZoomosCityId cid : allCityIds) {
            String site = cid.getSiteName();
            Set<String> expCities = ZoomosCheckService.parseCommaSeparated(cid.getCityIds());
            Map<String, Set<String>> addrMap2 = ZoomosCheckService.parseAddressMapping(cid.getAddressIds());
            if (!expCities.isEmpty() || !ZoomosCheckService.flattenAddressIds(addrMap2).isEmpty()) continue;
            // Есть завершённые данные — уже оценены в bySiteCity
            if (stats.stream().anyMatch(s -> site.equals(s.getSiteName()))) continue;
            // Ищем самую свежую in-progress запись по этому сайту
            ZoomosParsingStats ip = inProgressStatsFinal.stream()
                    .filter(s -> site.equals(s.getSiteName()) && s.getStartTime() != null)
                    .max(Comparator.comparing(ZoomosParsingStats::getStartTime))
                    .orElseGet(() -> inProgressStatsFinal.stream()
                            .filter(s -> site.equals(s.getSiteName()))
                            .findFirst().orElse(null));
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("site", site);
            issue.put("city", "");
            issue.put("cityId", "");
            issue.put("checkType", cid.getCheckType());
            issue.put("shopName", run.getShop().getShopName());
            addIssueStatus(issue, ip, null);
            issues.add(issue);
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
            g.put("isPriority", prioritySiteNames.contains(siteName));
            groups.add(g);
        });

        // Сортируем группы: приоритетные первые, затем ERROR → WARNING → OK, внутри — по имени сайта
        groups.sort(Comparator.comparingInt((Map<String, Object> g) -> Boolean.TRUE.equals(g.get("isPriority")) ? 0 : 1)
                .thenComparingInt(g -> statusPriority((String) g.get("status")))
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
        groups.sort(Comparator.comparingInt((Map<String, Object> g) -> Boolean.TRUE.equals(g.get("isPriority")) ? 0 : 1)
                .thenComparingInt(g -> statusPriority((String) g.get("status")))
                .thenComparing(g -> (String) g.get("siteName")));

        boolean canDeliver = issues.stream()
                .noneMatch(i -> "ERROR".equals(i.get("type"))
                        || "NOT_FOUND".equals(i.get("type"))
                        || "IN_PROGRESS".equals(i.get("type")));

        // ---- Исторический baseline-анализ ----
        int baselineDays = run.getBaselineDays() != null ? run.getBaselineDays() : 0;
        if (baselineDays > 0) {
            LocalDate baselineFrom = run.getDateFrom().minusDays(baselineDays);
            LocalDate baselineTo   = run.getDateFrom().minusDays(1);
            ZonedDateTime bFrom = baselineFrom.atStartOfDay(ZoneOffset.UTC);
            ZonedDateTime bTo   = baselineTo.atTime(23, 59).atZone(ZoneOffset.UTC);

            model.addAttribute("baselineDays", baselineDays);
            model.addAttribute("baselineFrom", baselineFrom.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            model.addAttribute("baselineTo",   baselineTo.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));

            // Для каждой группы site+city вычисляем trend
            for (Map.Entry<String, List<ZoomosParsingStats>> entry : bySiteCity.entrySet()) {
                List<ZoomosParsingStats> group = new ArrayList<>(entry.getValue());
                if (group.isEmpty()) continue;

                group.sort(Comparator.comparing(
                        s -> s.getStartTime() != null ? s.getStartTime() : ZonedDateTime.now(),
                        Comparator.naturalOrder()));
                ZoomosParsingStats current = group.get(group.size() - 1);
                if (current.getCompletionPercent() == null || current.getCompletionPercent() < 100) continue;

                String siteName = current.getSiteName();
                String cityName = current.getCityName();

                // Загружаем исторические данные для данного сайта+города
                List<ZoomosParsingStats> historical = parsingStatsRepository
                        .findForBaseline(siteName, cityName, bFrom, bTo);

                if (historical.size() < 3) continue; // недостаточно данных

                Map<String, Double> baseline = checkService.computeMedianBaseline(historical);
                List<String> trendWarnings = checkService.evaluateTrend(
                        current, baseline,
                        run.getTrendDropThreshold() != null ? run.getTrendDropThreshold() : 30,
                        run.getTrendErrorThreshold() != null ? run.getTrendErrorThreshold() : 100,
                        baselineFrom, baselineTo);

                for (String msg : trendWarnings) {
                    Map<String, Object> issue = new LinkedHashMap<>();
                    issue.put("site", siteName);
                    issue.put("city", cityName);
                    issue.put("cityId", ZoomosCheckService.extractCityId(cityName));
                    issue.put("addressId", current.getAddressId());
                    issue.put("checkType", current.getCheckType());
                    issue.put("shopName", run.getShop().getShopName());
                    issue.put("type", "TREND_WARNING");
                    issue.put("message", msg);
                    issues.add(issue);
                }
            }
        }

        // CSV для ИТ: сайт;город;тип;сообщение;ссылка_на_историю
        DateTimeFormatter itDateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String dateFromStr = run.getDateFrom().format(itDateFmt);
        String dateToStr   = run.getDateTo().format(itDateFmt);
        long ts = System.currentTimeMillis();

        // Блочный формат: группируем по типу проблемы
        List<Map<String, Object>> itErrors    = new ArrayList<>();
        List<Map<String, Object>> itNotFound  = new ArrayList<>();
        List<Map<String, Object>> itWarnings  = new ArrayList<>();
        for (Map<String, Object> issue : issues) {
            String t = (String) issue.get("type");
            if ("TREND_WARNING".equals(t)) continue;
            if ("ERROR".equals(t))                          itErrors.add(issue);
            else if ("NOT_FOUND".equals(t) || "IN_PROGRESS".equals(t)) itNotFound.add(issue);
            else if ("WARNING".equals(t))                   itWarnings.add(issue);
        }

        StringBuilder itText = new StringBuilder();
        for (Map.Entry<String, List<Map<String, Object>>> block : java.util.Arrays.asList(
                Map.entry("ОШИБКИ", itErrors),
                Map.entry("НЕТ ДАННЫХ", itNotFound),
                Map.entry("ПРЕДУПРЕЖДЕНИЯ", itWarnings)
        )) {
            if (block.getValue().isEmpty()) continue;
            if (itText.length() > 0) itText.append("\n");
            itText.append(block.getKey()).append("\n");

            for (Map<String, Object> issue : block.getValue()) {
                String site      = (String) issue.get("site");
                String city      = (String) issue.get("city");
                String msg       = (String) issue.get("message");
                String type      = (String) issue.get("type");
                String checkType = (String) issue.get("checkType");
                String shopName  = (String) issue.get("shopName");
                String cityId    = (String) issue.get("cityId");
                String addressId = (String) issue.get("addressId");
                if (cityId == null) cityId = "";
                String addrParam = (addressId != null && !addressId.isBlank()) ? addressId : "";

                String historyUrl = zoomosConfig.getBaseUrl()
                        + "/shops-parser/" + site + "/parsing-history"
                        + "?upd=" + ts
                        + "&dateFrom=" + dateFromStr + "&dateTo=" + dateToStr
                        + "&launchDate=&shop=" + ("API".equals(checkType) ? "-" : shopName)
                        + "&site=&cityId=" + cityId + "&addressId=" + addrParam + "&accountId=&server=";

                // Город с ID (например "3509 - Вологда") + адрес если задан
                String cityDisplay = (city != null && !city.isBlank()) ? city.trim() : "";
                if (!addrParam.isBlank()) cityDisplay += " (адрес " + addrParam + ")";

                String shortMsg = buildShortItMessage(type, msg, addrParam);

                // Строка: site — Город (ID)[ — проблема]
                StringBuilder line = new StringBuilder(site != null ? site : "");
                if (!cityDisplay.isBlank()) line.append(" — ").append(cityDisplay);
                if (!shortMsg.isBlank()) line.append(" — ").append(shortMsg);
                itText.append(line).append("\n");
                itText.append("  ").append(historyUrl).append("\n");
            }
        }

        // Считаем счётчики динамически из текущей оценки, чтобы не зависеть от устаревших run.warningCount
        long liveOkCount      = siteCityStatuses.values().stream().filter("OK"::equals).count();
        long liveWarnCount    = siteCityStatuses.values().stream().filter("WARNING"::equals).count();
        long liveErrCount     = siteCityStatuses.values().stream().filter("ERROR"::equals).count();
        long liveNotFoundCount = issues.stream()
                .filter(i -> "NOT_FOUND".equals(i.get("type")) || "IN_PROGRESS".equals(i.get("type")))
                .count();

        // Помечаем приоритетные сайты в issues
        for (Map<String, Object> issue : issues) {
            String site = (String) issue.get("site");
            issue.put("isPriority", site != null && prioritySiteNames.contains(site));
        }

        // Сортируем issues: приоритетные первые, затем ERROR → WARNING → IN_PROGRESS → NOT_FOUND → TREND_WARNING
        issues.sort(Comparator.comparingInt((Map<?, ?> issue) -> Boolean.TRUE.equals(issue.get("isPriority")) ? 0 : 1)
                .thenComparingInt(issue -> {
                    String t = (String) issue.get("type");
                    if ("ERROR".equals(t)) return 0;
                    if ("WARNING".equals(t)) return 1;
                    if ("IN_PROGRESS".equals(t)) return 2;
                    if ("NOT_FOUND".equals(t)) return 3;
                    return 4; // TREND_WARNING
                }));

        model.addAttribute("run", run);
        model.addAttribute("groups", groups);
        model.addAttribute("issues", issues);
        model.addAttribute("prioritySiteNames", prioritySiteNames);
        model.addAttribute("canDeliver", canDeliver);
        model.addAttribute("itText", itText.toString().trim());
        model.addAttribute("baseUrl", zoomosConfig.getBaseUrl());
        model.addAttribute("liveOkCount", liveOkCount);
        model.addAttribute("liveWarnCount", liveWarnCount);
        model.addAttribute("liveErrCount", liveErrCount);
        model.addAttribute("liveNotFoundCount", liveNotFoundCount);
        return "zoomos/check-results";
    }

    private void buildGroupIssues(List<ZoomosParsingStats> sortedAsc, String groupStatus,
                                    int dropThreshold, int errorGrowthThreshold,
                                    int minAbsoluteErrors, boolean ignoreStock,
                                    List<Map<String, Object>> issues, String shopName) {
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

        // WARNING: 100% выкачка, но нет товаров совсем — нужна проверка
        boolean alwaysZeroProducts = sortedAsc.stream()
                .allMatch(s -> s.getTotalProducts() == null || s.getTotalProducts() == 0);
        boolean allFullyComplete = sortedAsc.stream()
                .allMatch(s -> s.getCompletionPercent() != null && s.getCompletionPercent() >= 100);
        if (alwaysZeroProducts && allFullyComplete) {
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
            issue.put("addressId", addressId); issue.put("addressName", addressName);
            issue.put("checkType", checkType); issue.put("shopName", shopName);
            issue.put("type", "WARNING");
            issue.put("message", "100% выкачка, нет товаров — нужна проверка");
            issues.add(issue);
        }

        // ERROR: одиночная запись, есть товары, но в наличии 0
        if (!ignoreStock && sortedAsc.size() < 2
                && newest.getInStock() != null && newest.getInStock() == 0
                && newest.getTotalProducts() != null && newest.getTotalProducts() > 0) {
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
            issue.put("addressId", addressId); issue.put("addressName", addressName);
            issue.put("checkType", checkType); issue.put("shopName", shopName);
            issue.put("type", "ERROR");
            issue.put("message", "В наличии: 0 из " + newest.getTotalProducts() + " товаров");
            issues.add(issue);
        }

        if (sortedAsc.size() < 2) return;
        // Оцениваем только последнюю пару (текущее состояние)
        ZoomosParsingStats prev = sortedAsc.get(sortedAsc.size() - 2);

        // --- inStock-анализ (пропускается для сайтов с ignoreStock) ---
        if (!ignoreStock) {
            boolean alwaysZeroStock = sortedAsc.stream()
                    .allMatch(s -> s.getInStock() == null || s.getInStock() == 0);
            Integer prevStock = prev.getInStock();
            Integer newStock = newest.getInStock();

            if (!alwaysZeroStock && newStock != null && newStock == 0) {
                // Ищем последнее ненулевое значение в истории
                int lastNonZero = sortedAsc.subList(0, sortedAsc.size() - 1).stream()
                        .filter(s -> s.getInStock() != null && s.getInStock() > 0)
                        .mapToInt(ZoomosParsingStats::getInStock).max().orElse(0);
                String msg = lastNonZero > 0
                        ? String.format("В наличии: %d → 0 (−100%%)", lastNonZero)
                        : "В наличии: 0 (нет товаров в наличии)";
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
                issue.put("addressId", addressId); issue.put("addressName", addressName);
                issue.put("checkType", checkType); issue.put("shopName", shopName);
                issue.put("type", "ERROR");
                issue.put("message", msg);
                issues.add(issue);
            } else if (!alwaysZeroStock && prevStock != null && prevStock > 0 && newStock != null) {
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
            } else if (alwaysZeroStock) {
                boolean hasAnyProducts = sortedAsc.stream()
                        .anyMatch(s -> s.getTotalProducts() != null && s.getTotalProducts() > 0);
                if (hasAnyProducts) {
                    Map<String, Object> issue = new LinkedHashMap<>();
                    issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
                    issue.put("addressId", addressId); issue.put("addressName", addressName);
                    issue.put("checkType", checkType); issue.put("shopName", shopName);
                    issue.put("type", "WARNING");
                    issue.put("message", "В наличии: всегда 0 — нужна проверка");
                    issues.add(issue);
                }
            }
        }

        // WARNING: рост ошибок (только если достигнут минимальный абсолютный порог)
        int prevErr = prev.getErrorCount() != null ? prev.getErrorCount() : 0;
        int newErr = newest.getErrorCount() != null ? newest.getErrorCount() : 0;
        if (newErr >= minAbsoluteErrors) {
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
            } else if (prevErr == 0) {
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
                issue.put("addressId", addressId); issue.put("addressName", addressName);
                issue.put("checkType", checkType); issue.put("shopName", shopName);
                issue.put("type", "WARNING");
                issue.put("message", String.format("Ошибки парсинга: 0 → %d", newErr));
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
     * Добавляет поля currentIpPct/currentIpStart/currentIpUpd в NOT_FOUND issue.
     */
    private void putCurrentInProgress(Map<String, Object> issue, ZoomosParsingStats curIp) {
        issue.put("currentIpPct", curIp.getCompletionTotal() != null ? curIp.getCompletionTotal() : "?");
        issue.put("currentIpStart", curIp.getStartTime() != null
                ? curIp.getStartTime().format(DateTimeFormatter.ofPattern("dd.MM HH:mm")) : "?");
        if (curIp.getUpdatedTime() != null) {
            issue.put("currentIpUpd", curIp.getUpdatedTime().format(DateTimeFormatter.ofPattern("dd.MM HH:mm")));
        }
    }

    /**
     * Устанавливает тип и сообщение для issue на основе in-progress статистики.
     * IN_PROGRESS: показывает старт, процент выполнения и время обновления.
     * NOT_FOUND:   данных нет совсем. Если есть lastKnown — добавляет историческую информацию.
     */
    private void addIssueStatus(Map<String, Object> issue, ZoomosParsingStats ip, ZoomosParsingStats lastKnown) {
        if (ip == null) {
            issue.put("type", "NOT_FOUND");
            String aid = (String) issue.get("addressId");
            String cityId = (String) issue.get("cityId");
            if (aid != null && !aid.isBlank()) {
                issue.put("message", "Нет данных по адресу " + aid + " за указанный период");
            } else {
                issue.put("message", "Нет данных по городу " + (cityId != null ? cityId : "") + " за указанный период");
            }
            if (lastKnown != null && lastKnown.getStartTime() != null) {
                issue.put("lastKnownDate", lastKnown.getStartTime().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
                issue.put("lastKnownInStock", lastKnown.getInStock());
                issue.put("lastKnownTotal", lastKnown.getTotalProducts());
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

    /** Перегрузка без lastKnown — для обратной совместимости (IN_PROGRESS случай) */
    private void addIssueStatus(Map<String, Object> issue, ZoomosParsingStats ip) {
        addIssueStatus(issue, ip, null);
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
        Optional<ZoomosCheckRun> latestOpt = checkRunRepository.findFirstByShopIdOrderByStartedAtDesc(shopId);
        if (latestOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("runId", 0));
        }
        ZoomosCheckRun run = latestOpt.get();
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
        if (run.getStartedAt() != null) {
            result.put("startedAt", run.getStartedAt()
                    .withZoneSameInstant(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        }
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
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteKnownSite(@PathVariable Long id) {
        return knownSiteRepository.findById(id).map(site -> {
            String siteName = site.getSiteName();
            List<ZoomosCityId> cityIds = cityIdRepository.findAllBySiteName(siteName);
            cityIdRepository.deleteAll(cityIds);
            knownSiteRepository.delete(site);
            return ResponseEntity.ok(Map.<String, Object>of("success", true, "deletedCityIds", cityIds.size()));
        }).orElse(ResponseEntity.badRequest().body(Map.of("success", false, "error", "Сайт не найден")));
    }

    // =========================================================================
    // Расписания проверок /zoomos/schedule
    // =========================================================================

    @GetMapping("/schedule")
    public String schedulePage(Model model) {
        List<ZoomosShop> shops = parserService.getAllShops();
        Map<Long, ZoomosShopSchedule> schedules = new LinkedHashMap<>();
        Map<Long, String> lastRunFormatted = new LinkedHashMap<>();
        Map<Long, Long> lastRunIds = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        for (ZoomosShop shop : shops) {
            scheduleRepository.findByShopId(shop.getId()).ifPresent(s -> {
                schedules.put(shop.getId(), s);
                if (s.getLastRunAt() != null) {
                    lastRunFormatted.put(shop.getId(),
                            s.getLastRunAt().withZoneSameInstant(java.time.ZoneId.systemDefault()).format(fmt));
                }
            });
            checkRunRepository.findFirstByShopIdOrderByStartedAtDesc(shop.getId())
                    .ifPresent(run -> lastRunIds.put(shop.getId(), run.getId()));
        }
        model.addAttribute("shops", shops);
        model.addAttribute("schedules", schedules);
        model.addAttribute("lastRunFormatted", lastRunFormatted);
        model.addAttribute("lastRunIds", lastRunIds);
        return "zoomos/schedule";
    }

    @PostMapping("/schedule/{shopId}")
    public String saveSchedule(@PathVariable Long shopId,
                               @RequestParam(defaultValue = "0 8 * * *") String cronExpression,
                               @RequestParam(required = false) String timeFrom,
                               @RequestParam(required = false) String timeTo,
                               @RequestParam(defaultValue = "10") int dropThreshold,
                               @RequestParam(defaultValue = "30") int errorGrowthThreshold,
                               @RequestParam(defaultValue = "7") int baselineDays,
                               @RequestParam(defaultValue = "5") int minAbsoluteErrors,
                               @RequestParam(defaultValue = "-1") int dateOffsetFrom,
                               @RequestParam(defaultValue = "0") int dateOffsetTo,
                               @RequestParam(defaultValue = "30") int trendDropThreshold,
                               @RequestParam(defaultValue = "100") int trendErrorThreshold,
                               RedirectAttributes ra) {
        ZoomosShopSchedule schedule = scheduleRepository.findByShopId(shopId)
                .orElse(ZoomosShopSchedule.builder().shopId(shopId).build());
        schedule.setCronExpression(cronExpression.trim());
        schedule.setTimeFrom(timeFrom != null && !timeFrom.isBlank() ? timeFrom : null);
        schedule.setTimeTo(timeTo != null && !timeTo.isBlank() ? timeTo : null);
        schedule.setDropThreshold(dropThreshold);
        schedule.setErrorGrowthThreshold(errorGrowthThreshold);
        schedule.setBaselineDays(baselineDays);
        schedule.setMinAbsoluteErrors(Math.max(0, minAbsoluteErrors));
        schedule.setDateOffsetFrom(dateOffsetFrom);
        schedule.setDateOffsetTo(dateOffsetTo);
        schedule.setTrendDropThreshold(Math.max(1, trendDropThreshold));
        schedule.setTrendErrorThreshold(Math.max(1, trendErrorThreshold));
        schedulerService.saveAndReschedule(schedule);
        ra.addFlashAttribute("success", "Расписание сохранено");
        return "redirect:/zoomos/schedule";
    }

    @PostMapping("/schedule/{shopId}/toggle")
    public String toggleSchedule(@PathVariable Long shopId, RedirectAttributes ra) {
        schedulerService.toggleEnabled(shopId);
        ra.addFlashAttribute("success", "Статус расписания изменён");
        return "redirect:/zoomos/schedule";
    }

    @PostMapping("/schedule/{shopId}/delete")
    public String deleteSchedule(@PathVariable Long shopId, RedirectAttributes ra) {
        schedulerService.deleteSchedule(shopId);
        ra.addFlashAttribute("success", "Расписание удалено");
        return "redirect:/zoomos/schedule";
    }

    // =========================================================================
    // Priority alerts API
    // =========================================================================

    @GetMapping("/api/priority-alerts")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> priorityAlerts() {
        try {
            List<ZoomosKnownSite> prioritySites = knownSiteRepository.findAllByIsPriorityTrue();
            if (prioritySites.isEmpty()) return ResponseEntity.ok(List.of());

            // Регистронезависимый словарь: lowercase → canonical ZoomosKnownSite
            Map<String, ZoomosKnownSite> priorityByNameLower = prioritySites.stream()
                    .collect(Collectors.toMap(
                            s -> s.getSiteName().toLowerCase(),
                            s -> s,
                            (a, b) -> a));
            Set<String> priorityNamesLower = priorityByNameLower.keySet();

            ZonedDateTime startOfDay = java.time.LocalDate.now().atStartOfDay(ZoneOffset.UTC);
            List<ZoomosCheckRun> todayRuns = checkRunRepository.findCompletedToday(startOfDay);

            // Берём последний run для каждого магазина
            Map<Long, ZoomosCheckRun> latestByShop = new LinkedHashMap<>();
            for (ZoomosCheckRun run : todayRuns) {
                latestByShop.putIfAbsent(run.getShop().getId(), run);
            }

            // siteName (canonical) → alert map
            Map<String, Map<String, Object>> siteAlerts = new LinkedHashMap<>();

            for (ZoomosCheckRun run : latestByShop.values()) {
                List<ZoomosParsingStats> stats = parsingStatsRepository
                        .findByCheckRunIdAndIsBaselineFalseOrderBySiteNameAscCityNameAsc(run.getId());

                // Регистронезависимая группировка по siteName+cityName (только приоритетные)
                Map<String, List<ZoomosParsingStats>> bySiteCity = new LinkedHashMap<>();
                for (ZoomosParsingStats s : stats) {
                    if (s.getSiteName() == null) continue;
                    if (!priorityNamesLower.contains(s.getSiteName().toLowerCase())) continue;
                    String key = s.getSiteName() + "|" + (s.getCityName() != null ? s.getCityName() : "");
                    bySiteCity.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
                }

                int drop = run.getDropThreshold() != null ? run.getDropThreshold() : 10;
                int errGrowth = run.getErrorGrowthThreshold() != null ? run.getErrorGrowthThreshold() : 30;
                int minAbsErr = run.getMinAbsoluteErrors() != null ? run.getMinAbsoluteErrors() : 5;

                // Загружаем ignoreStockSites для этого run
                Set<String> ignoreStockSitesAlert = knownSiteRepository.findAllByIgnoreStockTrue()
                        .stream().map(ZoomosKnownSite::getSiteName).collect(Collectors.toSet());

                // Оцениваем группы с данными
                for (Map.Entry<String, List<ZoomosParsingStats>> entry : bySiteCity.entrySet()) {
                    List<ZoomosParsingStats> group = new ArrayList<>(entry.getValue());
                    group.sort(Comparator.comparing(
                            s -> s.getStartTime() != null ? s.getStartTime() : ZonedDateTime.now(),
                            Comparator.naturalOrder()));
                    boolean ignoreStk = group.get(0).getSiteName() != null
                            && ignoreStockSitesAlert.contains(group.get(0).getSiteName());
                    String status = checkService.evaluateGroup(group, drop, errGrowth, minAbsErr, ignoreStk);
                    if ("OK".equals(status)) continue;

                    String rawName = group.get(0).getSiteName();
                    ZoomosKnownSite ks = priorityByNameLower.get(rawName.toLowerCase());
                    String canonical = ks != null ? ks.getSiteName() : rawName;
                    mergePriorityAlert(siteAlerts, canonical, status);
                }

                // NOT_FOUND: ожидаемые приоритетные сайты без данных в этом run
                Set<String> sitesWithStatsLower = stats.stream()
                        .filter(s -> s.getSiteName() != null)
                        .map(s -> s.getSiteName().toLowerCase())
                        .collect(Collectors.toSet());

                List<ZoomosCityId> shopCityIds = parserService.getCityIds(run.getShop().getId());
                for (ZoomosCityId cid : shopCityIds) {
                    if (!Boolean.TRUE.equals(cid.getIsActive())) continue;
                    if (cid.getSiteName() == null) continue;
                    String nameLower = cid.getSiteName().toLowerCase();
                    if (!priorityNamesLower.contains(nameLower)) continue;
                    if (sitesWithStatsLower.contains(nameLower)) continue; // данные есть

                    ZoomosKnownSite ks = priorityByNameLower.get(nameLower);
                    String canonical = ks != null ? ks.getSiteName() : cid.getSiteName();
                    mergePriorityAlert(siteAlerts, canonical, "NOT_FOUND");
                }
            }

            return ResponseEntity.ok(new ArrayList<>(siteAlerts.values()));
        } catch (Exception e) {
            log.error("Ошибка priority-alerts: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    private void mergePriorityAlert(Map<String, Map<String, Object>> siteAlerts,
                                    String siteName, String status) {
        siteAlerts.compute(siteName, (k, existing) -> {
            if (existing == null) {
                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("siteName", siteName);
                alert.put("severity", status);
                alert.put("issueCount", 1);
                return alert;
            } else {
                existing.put("issueCount", (int) existing.get("issueCount") + 1);
                // Приоритет тяжести: ERROR > NOT_FOUND > WARNING
                String cur = (String) existing.get("severity");
                if ("ERROR".equals(status)
                        || ("NOT_FOUND".equals(status) && !"ERROR".equals(cur))
                        || ("WARNING".equals(status) && "OK".equals(cur))) {
                    existing.put("severity", status);
                }
                return existing;
            }
        });
    }

    // =========================================================================
    // Priority toggle для справочника сайтов
    // =========================================================================

    @PostMapping("/sites/{id}/priority")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleSitePriority(@PathVariable Long id) {
        return knownSiteRepository.findById(id).map(site -> {
            site.setPriority(!site.isPriority());
            knownSiteRepository.save(site);
            return ResponseEntity.ok(Map.<String, Object>of("success", true, "isPriority", site.isPriority()));
        }).orElse(ResponseEntity.badRequest().body(Map.of("success", false, "error", "Сайт не найден")));
    }

    @PostMapping("/sites/{id}/ignore-stock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleIgnoreStock(@PathVariable Long id) {
        return knownSiteRepository.findById(id).map(site -> {
            site.setIgnoreStock(!site.isIgnoreStock());
            knownSiteRepository.save(site);
            return ResponseEntity.ok(Map.<String, Object>of("success", true, "ignoreStock", site.isIgnoreStock()));
        }).orElse(ResponseEntity.badRequest().body(Map.of("success", false, "error", "Сайт не найден")));
    }

    @PostMapping("/sites/by-name/priority")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleSitePriorityByName(@RequestParam String siteName) {
        if (siteName == null || siteName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Не указан siteName"));
        }
        ZoomosKnownSite site = knownSiteRepository.findBySiteName(siteName.trim().toLowerCase())
                .orElseGet(() -> knownSiteRepository.save(
                        ZoomosKnownSite.builder()
                                .siteName(siteName.trim().toLowerCase())
                                .checkType("ITEM")
                                .build()));
        site.setPriority(!site.isPriority());
        knownSiteRepository.save(site);
        return ResponseEntity.ok(Map.of("success", true, "isPriority", site.isPriority()));
    }

    /**
     * Краткое человечное описание проблемы для "Текст для ИТ".
     * Убирает технические фразы ("всегда", "нужна проверка"), упрощает стрелки.
     */
    private static String buildShortItMessage(String type, String msg, String addrParam) {
        if ("NOT_FOUND".equals(type)) {
            return ""; // адрес уже в части "город (адрес X)" выше
        }
        if ("IN_PROGRESS".equals(type)) {
            if (msg != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)%").matcher(msg);
                if (m.find()) return "выкачка в процессе (" + m.group(1) + "%)";
            }
            return "выкачка в процессе";
        }
        if (msg == null || msg.isBlank()) return "";

        // "В наличии: 500 → 0 (−100%)" → "в наличии 0 (было 500)"
        java.util.regex.Matcher mStock = java.util.regex.Pattern
                .compile("В наличии: (\\d+) → 0").matcher(msg);
        if (mStock.find()) return "в наличии 0 (было " + mStock.group(1) + ")";

        // "В наличии: 0 ..." → "в наличии 0"
        if (msg.startsWith("В наличии: 0")) return "в наличии 0";

        // "Падение 'В наличии': 500 → 300 (−40%)" → "в наличии: 500 → 300 (−40%)"
        if (msg.startsWith("Падение 'В наличии':")) {
            return "в наличии:" + msg.substring("Падение 'В наличии':".length());
        }

        // "Рост ошибок: 5 → 50 (+900%)" → "ошибки: 5 → 50"
        if (msg.startsWith("Рост ошибок:")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+) → (\\d+)").matcher(msg);
            if (m.find()) return "ошибки: " + m.group(1) + " → " + m.group(2);
        }

        // "Ошибки парсинга: 0 → N" → "ошибки: N"
        if (msg.startsWith("Ошибки парсинга:")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("→ (\\d+)").matcher(msg);
            if (m.find()) return "ошибки: " + m.group(1);
        }

        // "Падение товаров: 1000 → 500 (−50%)" → "товаров: 1000 → 500 (−50%)"
        if (msg.startsWith("Падение товаров:")) {
            return "товаров:" + msg.substring("Падение товаров:".length());
        }

        // "100% выкачка, нет товаров — нужна проверка" → "нет товаров"
        if (msg.contains("нет товаров")) return "нет товаров";

        // "В наличии: всегда 0 — нужна проверка" → "в наличии 0"
        if (msg.contains("всегда 0")) return "в наличии 0";

        // Прочее: отдаём как есть, строчная первая буква
        return Character.toLowerCase(msg.charAt(0)) + msg.substring(1);
    }
}
