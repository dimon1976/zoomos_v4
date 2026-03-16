package com.java.controller;

import com.java.config.ZoomosConfig;
import com.java.model.entity.*;
import com.java.service.RedmineService;
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
import com.java.service.ZoomosSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.Comparator;
import java.util.Objects;
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
    private final RedmineService redmineService;
    private final ZoomosSettingsService settingsService;

    @Autowired
    @Qualifier("zoomosCheckExecutor")
    private java.util.concurrent.Executor zoomosCheckExecutor;

    @GetMapping({"", "/"})
    public String index(Model model) {
        List<ZoomosShop> allShops = parserService.getAllShops();
        List<ZoomosShop> shops = allShops.stream().filter(ZoomosShop::isEnabled)
                .sorted(Comparator.comparing(ZoomosShop::isPriority).reversed())
                .collect(Collectors.toList());
        List<ZoomosShop> disabledShops = allShops.stream().filter(s -> !s.isEnabled()).collect(Collectors.toList());

        Map<Long, List<ZoomosCityId>> cityIdsMap = new java.util.LinkedHashMap<>();
        for (ZoomosShop shop : allShops) {
            cityIdsMap.put(shop.getId(), parserService.getCityIds(shop.getId()));
        }
        Map<String, String> cityNamesMap = cityNameRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ZoomosCityName::getCityId, ZoomosCityName::getCityName));

        // Расписания для badge'ей вкл/выкл на каждой карточке.
        // Если у магазина несколько расписаний, берём включённое (если есть), иначе любое первое.
        Map<Long, ZoomosShopSchedule> schedulesMap = new java.util.LinkedHashMap<>();
        for (ZoomosShop shop : allShops) {
            List<ZoomosShopSchedule> shopScheds = scheduleRepository.findAllByShopId(shop.getId());
            if (!shopScheds.isEmpty()) {
                ZoomosShopSchedule representative = shopScheds.stream()
                        .filter(ZoomosShopSchedule::isEnabled).findFirst()
                        .orElse(shopScheds.get(0));
                schedulesMap.put(shop.getId(), representative);
            }
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
        Map<String, String> dt = settingsService.getAllSettings();
        model.addAttribute("defaultThresholds", dt);
        model.addAttribute("defDropThreshold",      dt.getOrDefault("default.drop_threshold", "10"));
        model.addAttribute("defErrThreshold",       dt.getOrDefault("default.error_growth_threshold", "30"));
        model.addAttribute("defBaselineDays",       dt.getOrDefault("default.baseline_days", "7"));
        model.addAttribute("defMinAbsErrors",       dt.getOrDefault("default.min_absolute_errors", "5"));
        model.addAttribute("defTrendDrop",          dt.getOrDefault("default.trend_drop_threshold", "30"));
        model.addAttribute("defTrendErr",           dt.getOrDefault("default.trend_error_threshold", "100"));
        return "zoomos/index";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        Map<String, String> allowed = Map.of(
                "default.drop_threshold",         params.getOrDefault("default.drop_threshold", "10"),
                "default.error_growth_threshold", params.getOrDefault("default.error_growth_threshold", "30"),
                "default.baseline_days",          params.getOrDefault("default.baseline_days", "7"),
                "default.min_absolute_errors",    params.getOrDefault("default.min_absolute_errors", "5"),
                "default.trend_drop_threshold",   params.getOrDefault("default.trend_drop_threshold", "30"),
                "default.trend_error_threshold",  params.getOrDefault("default.trend_error_threshold", "100")
        );
        settingsService.saveAll(allowed);
        ra.addFlashAttribute("success", "Настройки по умолчанию сохранены");
        return "redirect:/zoomos";
    }

    /** AJAX-toggle приоритетности магазина */
    @PostMapping("/shops/{shopId}/priority")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleShopPriority(@PathVariable Long shopId) {
        boolean isPriority = parserService.togglePriority(shopId);
        return ResponseEntity.ok(Map.of("success", true, "isPriority", isPriority));
    }

    /** AJAX-toggle расписания (для index.html без перезагрузки) — переключает ВСЕ расписания магазина */
    @PostMapping("/api/schedule/{shopId}/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleScheduleAjax(@PathVariable Long shopId) {
        schedulerService.toggleAllByShopId(shopId);
        boolean isEnabled = scheduleRepository.findAllByShopId(shopId).stream()
                .anyMatch(ZoomosShopSchedule::isEnabled);
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

            // Запускаем в фоне через выделенный пул (для параллельного запуска нескольких магазинов)
            CompletableFuture.runAsync(() -> {
                try {
                    checkService.runCheck(shopId, from, to, tf, tt, dropThreshold, errorGrowthThreshold, bl, mae, tdt, tet, operationId);
                    // Обновляем lastRunAt во всех расписаниях магазина
                    java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
                    scheduleRepository.findAllByShopId(shopId).forEach(schedule -> {
                        schedule.setLastRunAt(now);
                        scheduleRepository.save(schedule);
                    });
                } catch (Exception e) {
                    log.error("Ошибка фоновой проверки: {}", e.getMessage(), e);
                }
            }, zoomosCheckExecutor);

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
        // независимо от timeFrom. Поэтому нижнюю границу сравниваем только по дате.
        // Верхняя граница (timeTo): выкачки, стартовавшие ПОСЛЕ timeTo, не относятся к
        // проверяемому окну → исключаем, чтобы не показывать нерелевантные «в процессе».
        ZonedDateTime effectiveRangeEnd = null;
        if (run.getTimeTo() != null && !run.getTimeTo().isBlank()) {
            effectiveRangeEnd = run.getDateTo().atTime(LocalTime.parse(run.getTimeTo())).atZone(ZoneOffset.UTC);
        }
        final ZonedDateTime finalRangeEnd = effectiveRangeEnd;
        inProgressStats = inProgressStats.stream()
                .filter(ip -> (ip.getCompletionPercent() == null || ip.getCompletionPercent() < 100)
                        && (ip.getStartTime() == null
                            || !ip.getStartTime().toLocalDate().isBefore(run.getDateFrom()))
                        // верхняя граница: старт не позже timeTo (если timeTo задан)
                        && (finalRangeEnd == null || ip.getStartTime() == null
                            || !ip.getStartTime().isAfter(finalRangeEnd)))
                .collect(Collectors.toList());

        int dropThreshold = run.getDropThreshold() != null ? run.getDropThreshold() : 10;
        int errorGrowthThreshold = run.getErrorGrowthThreshold() != null ? run.getErrorGrowthThreshold() : 30;
        int minAbsoluteErrors = run.getMinAbsoluteErrors() != null ? run.getMinAbsoluteErrors() : 5;

        // Baseline: вычисляем диапазон дат заранее (используется и в оценке групп, и в тренд-анализе)
        int baselineDays = run.getBaselineDays() != null ? run.getBaselineDays() : 0;
        ZonedDateTime baselineDatesFrom = null, baselineDatesTo = null;
        if (baselineDays > 0) {
            LocalDate blFrom = run.getDateFrom().minusDays(baselineDays);
            LocalDate blTo   = run.getDateFrom().minusDays(1);
            baselineDatesFrom = blFrom.atStartOfDay(ZoneOffset.UTC);
            baselineDatesTo   = blTo.atTime(23, 59).atZone(ZoneOffset.UTC);
        }

        Set<String> ignoreStockSites = knownSiteRepository.findAllByIgnoreStockTrue()
                .stream().map(ZoomosKnownSite::getSiteName).collect(Collectors.toSet());
        Set<String> prioritySiteNames = knownSiteRepository.findAllByIsPriorityTrue()
                .stream().map(ZoomosKnownSite::getSiteName).collect(Collectors.toSet());

        // Предзагружаем baseline-записи текущего run один раз.
        // Фильтруем по check_run_id — иначе findForBaseline включает записи других run
        // в те же даты, что даёт неверную медиану.
        List<ZoomosParsingStats> baselineStatsList = parsingStatsRepository
                .findByCheckRunIdAndIsBaselineTrueOrderByStartTimeDesc(runId);
        Map<String, List<ZoomosParsingStats>> baselineByKey = new LinkedHashMap<>();
        for (ZoomosParsingStats s : baselineStatsList) {
            String key = s.getSiteName() + "|" + (s.getCityName() != null ? s.getCityName() : "")
                    + "|" + (s.getAddressId() != null ? s.getAddressId() : "");
            baselineByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

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

        // Загружаем конфигурацию city_ids (нужна и для парсер-фильтра, и для NOT_FOUND проверки)
        List<ZoomosCityId> allCityIds = parserService.getCityIds(run.getShop().getId())
                .stream().filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .collect(Collectors.toList());

        // Карта siteName → cityId config для сайтов с парсер-фильтром
        Map<String, ZoomosCityId> cityIdBySite = allCityIds.stream()
                .filter(c -> c.getParserInclude() != null && !c.getParserInclude().isBlank())
                .collect(Collectors.toMap(ZoomosCityId::getSiteName, c -> c, (a, b) -> a));

        // Статус каждой связки site+city
        Map<String, String> siteCityStatuses = new LinkedHashMap<>();
        for (Map.Entry<String, List<ZoomosParsingStats>> entry : bySiteCity.entrySet()) {
            List<ZoomosParsingStats> group = new ArrayList<>(entry.getValue());
            group.sort(Comparator.comparing(
                    s -> s.getStartTime() != null ? s.getStartTime() : ZonedDateTime.now(),
                    Comparator.naturalOrder()));

            String groupKey = entry.getKey();
            String siteName = groupKey.split("\\|")[0];
            ZoomosCityId cidConfig = cityIdBySite.get(siteName);

            String status;
            if (cidConfig != null) {
                // Парсер-фильтр задан: completeness-check ВМЕСТО evaluateGroup.
                // Добавляем in-progress stats с тем же siteName, чтобы видеть прогресс незавершённых парсеров.
                List<ZoomosParsingStats> groupExt = new ArrayList<>(group);
                final String siteNameForFilter = siteName;
                inProgressStats.stream()
                        .filter(ip -> siteNameForFilter.equals(ip.getSiteName()))
                        .forEach(groupExt::add);
                List<Map<String, Object>> incomplete = checkService.checkParserCompleteness(
                        groupExt, cidConfig.getParserInclude(), cidConfig.getParserIncludeMode());
                if (incomplete == null || incomplete.isEmpty()) {
                    status = "OK";
                } else {
                    status = "ERROR"; // и OR, и AND: незавершённые парсеры = ERROR
                    String cityPart = groupKey.contains("|") ? groupKey.split("\\|", 2)[1] : "";
                    String cityId = ZoomosCheckService.extractCityId(cityPart);
                    for (Map<String, Object> info : incomplete) {
                        Map<String, Object> iss = new LinkedHashMap<>();
                        iss.put("site", siteName);
                        iss.put("city", cityPart);
                        iss.put("cityId", cityId != null ? cityId : cityPart);
                        iss.put("checkType", cidConfig.getCheckType());
                        iss.put("shopName", run.getShop().getShopName());
                        iss.put("isPriority", prioritySiteNames.contains(siteName));
                        iss.put("type", status);
                        String pattern = (String) info.get("pattern");
                        StringBuilder msg = new StringBuilder();
                        if (Boolean.TRUE.equals(info.get("orMode"))) {
                            msg.append("Ни один парсер '").append(pattern).append("' не завершён");
                        } else {
                            msg.append("Парсер '").append(pattern).append("' не завершён");
                        }
                        if (Boolean.TRUE.equals(info.get("noData"))) {
                            msg.append(" (нет данных)");
                        } else {
                            Object completion = info.get("completion");
                            Object total = info.get("total");
                            Object inStock = info.get("inStock");
                            Object errors = info.get("errors");
                            Object desc = info.get("description");
                            Object updatedTime = info.get("updatedTime");
                            if (completion != null) msg.append(": ").append(completion);
                            if (total != null) msg.append(", товаров: ").append(total);
                            if (inStock != null) msg.append(", в наличии: ").append(inStock);
                            if (errors != null && !Integer.valueOf(0).equals(errors)) msg.append(", ошибок: ").append(errors);
                            if (updatedTime instanceof ZonedDateTime) msg.append(", обновлено: ")
                                    .append(((ZonedDateTime) updatedTime).format(DateTimeFormatter.ofPattern("HH:mm")));
                            if (desc != null) msg.append(" [").append(desc).append("]");
                        }
                        iss.put("message", msg.toString());
                        issues.add(iss);
                    }
                }
            } else {
                // Без фильтра: evaluateGroup с baseline-медианой
                boolean ignoreStock = group.get(0).getSiteName() != null
                        && ignoreStockSites.contains(group.get(0).getSiteName());
                ZoomosCheckService.MedianStats groupBaseline = null;
                if (baselineDatesFrom != null) {
                    String siteForBl = group.get(0).getSiteName();
                    String cityForBl = group.get(0).getCityName();
                    String addrForBl = group.get(0).getAddressId();
                    String blKey = siteForBl + "|" + (cityForBl != null ? cityForBl : "")
                            + "|" + (addrForBl != null ? addrForBl : "");
                    List<ZoomosParsingStats> bl = baselineByKey.getOrDefault(blKey, Collections.emptyList());
                    groupBaseline = checkService.computeBaselineMedian(bl);
                }
                status = checkService.evaluateGroup(group, dropThreshold, errorGrowthThreshold,
                        minAbsoluteErrors, ignoreStock, groupBaseline);
                if (!"OK".equals(status)) {
                    buildGroupIssues(group, status, dropThreshold, errorGrowthThreshold,
                            minAbsoluteErrors, ignoreStock, groupBaseline, issues, run.getShop().getShopName());
                }
            }
            siteCityStatuses.put(groupKey, status);
        }

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

        // Batch pre-load для NOT_FOUND lookup — избегаем N+1 SQL
        String[] expectedSiteNames = allCityIds.stream().map(ZoomosCityId::getSiteName)
                .distinct().toArray(String[]::new);
        Set<String> allExpectedAddrIds = new HashSet<>();
        for (ZoomosCityId cid : allCityIds) {
            allExpectedAddrIds.addAll(ZoomosCheckService.flattenAddressIds(
                    ZoomosCheckService.parseAddressMapping(cid.getAddressIds())));
        }
        Map<String, ZoomosParsingStats> lastFinishedByAddrKey = new HashMap<>();
        if (expectedSiteNames.length > 0 && !allExpectedAddrIds.isEmpty()) {
            parsingStatsRepository.findLatestFinishedBySiteAndAddressIds(
                    expectedSiteNames, allExpectedAddrIds.toArray(String[]::new))
                    .forEach(s -> lastFinishedByAddrKey.put(s.getSiteName() + "|" + s.getAddressId(), s));
        }
        Map<String, ZoomosParsingStats> lastFinishedByCityBatchKey = new HashMap<>();
        if (expectedSiteNames.length > 0) {
            parsingStatsRepository.findLatestFinishedBySites(expectedSiteNames)
                    .forEach(s -> {
                        String cId = ZoomosCheckService.extractCityId(s.getCityName());
                        if (cId != null) lastFinishedByCityBatchKey.putIfAbsent(s.getSiteName() + "|" + cId, s);
                    });
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
                ZoomosParsingStats lastKnownAddr = (ip == null && aid != null)
                        ? lastFinishedByAddrKey.get(site + "|" + aid)
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
                        ? lastFinishedByCityBatchKey.get(site + "|" + cityId)
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

        boolean canDeliver = issues.stream()
                .noneMatch(i -> "ERROR".equals(i.get("type")));

        // ---- Исторический baseline-анализ (тренды) ----
        // baselineDays и baselineDatesFrom/To уже вычислены выше
        if (baselineDays > 0) {
            LocalDate baselineFrom = run.getDateFrom().minusDays(baselineDays);
            LocalDate baselineTo   = run.getDateFrom().minusDays(1);

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

                // Используем предзагруженные baseline-записи текущего run
                String blKey = siteName + "|" + (cityName != null ? cityName : "");
                List<ZoomosParsingStats> historical = baselineByKey.getOrDefault(blKey, Collections.emptyList());

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

        // Считаем счётчики динамически из текущей оценки, чтобы не зависеть от устаревших run.warningCount
        long liveOkCount = 0, liveWarnCount = 0, liveErrCount = 0;
        for (String s : siteCityStatuses.values()) {
            if ("OK".equals(s))           liveOkCount++;
            else if ("WARNING".equals(s)) liveWarnCount++;
            else if ("ERROR".equals(s))   liveErrCount++;
        }
        // liveNotFoundCount — число городов/адресов, по которым нет данных (IN_PROGRESS или совсем нет)
        long liveNotFoundCount = issues.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("noData")))
                .count();

        // Помечаем приоритетные сайты в issues
        for (Map<String, Object> issue : issues) {
            String site = (String) issue.get("site");
            issue.put("isPriority", site != null && prioritySiteNames.contains(site));
        }

        // Сортируем issues: приоритетные первые, затем ERROR → WARNING → TREND_WARNING
        issues.sort(Comparator.comparingInt((Map<?, ?> issue) -> Boolean.TRUE.equals(issue.get("isPriority")) ? 0 : 1)
                .thenComparingInt(issue -> {
                    String t = (String) issue.get("type");
                    if ("ERROR".equals(t)) return 0;
                    if ("WARNING".equals(t)) return 1;
                    return 2; // TREND_WARNING
                }));

        // Разделяем issues: главные (ERROR/WARNING) и тренды (TREND_WARNING)
        List<Map<String, Object>> mainIssues  = issues.stream()
                .filter(i -> !"TREND_WARNING".equals(i.get("type"))).toList();
        List<Map<String, Object>> trendIssues = issues.stream()
                .filter(i ->  "TREND_WARNING".equals(i.get("type"))).toList();

        // Карта siteName → parserInclude для отображения в колонке "Парсер" (только фильтр, не полный parserDescription)
        Map<String, String> parserIncludeBysite = new HashMap<>();
        cityIdBySite.forEach((site, cid) -> parserIncludeBysite.put(site, cid.getParserInclude()));

        // Группируем mainIssues по сайту — для Block 2 (per-site collapsibles)
        Map<String, List<Map<String, Object>>> issuesBySite = new LinkedHashMap<>();
        for (Map<String, Object> issue : mainIssues) {
            String site = (String) issue.get("site");
            issuesBySite.computeIfAbsent(site, k -> new ArrayList<>()).add(issue);
        }
        // Счётчики для заголовков в Block 2
        Map<String, Integer> errorCountBySite = new HashMap<>();
        Map<String, Integer> warnCountBySite  = new HashMap<>();
        for (Map<String, Object> issue : mainIssues) {
            String site = (String) issue.get("site");
            if ("ERROR".equals(issue.get("type"))) errorCountBySite.merge(site, 1, Integer::sum);
            else                                    warnCountBySite.merge(site, 1, Integer::sum);
        }

        model.addAttribute("run", run);
        model.addAttribute("issues", mainIssues);
        model.addAttribute("issuesBySite", issuesBySite);
        model.addAttribute("errorCountBySite", errorCountBySite);
        model.addAttribute("warnCountBySite", warnCountBySite);
        model.addAttribute("trendIssues", trendIssues);
        model.addAttribute("parserIncludeBysite", parserIncludeBysite);
        model.addAttribute("prioritySiteNames", prioritySiteNames);
        model.addAttribute("canDeliver", canDeliver);
        model.addAttribute("baseUrl", zoomosConfig.getBaseUrl());
        model.addAttribute("liveOkCount", liveOkCount);
        model.addAttribute("liveWarnCount", liveWarnCount);
        model.addAttribute("liveErrCount", liveErrCount);
        model.addAttribute("liveNotFoundCount", liveNotFoundCount);

        // Redmine: загружаем из БД для всех сайтов (mainIssues + groups)
        // Статусы обновляются async через JS /check-batch после загрузки страницы
        model.addAttribute("redmineEnabled", redmineService.isEnabled());
        if (redmineService.isEnabled()) {
            Set<String> allSiteNames = new LinkedHashSet<>();
            mainIssues.forEach(i -> { String s = (String) i.get("site"); if (s != null) allSiteNames.add(s); });
            if (!allSiteNames.isEmpty()) {
                List<com.java.model.entity.ZoomosRedmineIssue> existing =
                        redmineService.findAllBySiteNames(allSiteNames);
                model.addAttribute("redmineIssues",
                        existing.stream().collect(Collectors.toMap(
                                com.java.model.entity.ZoomosRedmineIssue::getSiteName, e -> e)));
            } else {
                model.addAttribute("redmineIssues", Collections.emptyMap());
            }
        } else {
            model.addAttribute("redmineIssues", Collections.emptyMap());
        }

        return "zoomos/check-results";
    }

    /**
     * Lazy-load endpoint: возвращает Thymeleaf-фрагмент "groupsBlock" со всеми деталями выкачки.
     * Вызывается из JS при первом раскрытии блока деталей.
     */
    @GetMapping("/check/results/{runId}/groups")
    public String checkResultsGroups(@PathVariable Long runId, Model model) {
        ZoomosCheckRun run = checkRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Проверка не найдена: " + runId));

        List<ZoomosParsingStats> allStatsList = parsingStatsRepository
                .findByCheckRunIdAndIsBaselineFalseOrderBySiteNameAscCityNameAsc(runId);

        List<ZoomosParsingStats> stats = allStatsList.stream()
                .filter(s -> !Boolean.FALSE.equals(s.getIsFinished()))
                .collect(Collectors.toCollection(ArrayList::new));
        List<ZoomosParsingStats> inProgressStats = allStatsList.stream()
                .filter(s -> Boolean.FALSE.equals(s.getIsFinished()))
                .collect(Collectors.toList());

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
        ZonedDateTime effectiveRangeEnd = null;
        if (run.getTimeTo() != null && !run.getTimeTo().isBlank()) {
            effectiveRangeEnd = run.getDateTo().atTime(LocalTime.parse(run.getTimeTo())).atZone(ZoneOffset.UTC);
        }
        final ZonedDateTime finalRangeEnd = effectiveRangeEnd;
        inProgressStats = inProgressStats.stream()
                .filter(ip -> (ip.getCompletionPercent() == null || ip.getCompletionPercent() < 100)
                        && (ip.getStartTime() == null
                            || !ip.getStartTime().toLocalDate().isBefore(run.getDateFrom()))
                        && (finalRangeEnd == null || ip.getStartTime() == null
                            || !ip.getStartTime().isAfter(finalRangeEnd)))
                .collect(Collectors.toList());

        int dropThreshold = run.getDropThreshold() != null ? run.getDropThreshold() : 10;
        int errorGrowthThreshold = run.getErrorGrowthThreshold() != null ? run.getErrorGrowthThreshold() : 30;
        int minAbsoluteErrors = run.getMinAbsoluteErrors() != null ? run.getMinAbsoluteErrors() : 5;

        int baselineDays = run.getBaselineDays() != null ? run.getBaselineDays() : 0;
        ZonedDateTime baselineDatesFrom = null;
        if (baselineDays > 0) {
            baselineDatesFrom = run.getDateFrom().minusDays(baselineDays).atStartOfDay(ZoneOffset.UTC);
        }

        Set<String> ignoreStockSites = knownSiteRepository.findAllByIgnoreStockTrue()
                .stream().map(ZoomosKnownSite::getSiteName).collect(Collectors.toSet());
        Set<String> prioritySiteNames = knownSiteRepository.findAllByIsPriorityTrue()
                .stream().map(ZoomosKnownSite::getSiteName).collect(Collectors.toSet());

        List<ZoomosParsingStats> baselineStatsList = parsingStatsRepository
                .findByCheckRunIdAndIsBaselineTrueOrderByStartTimeDesc(runId);
        Map<String, List<ZoomosParsingStats>> baselineByKey = new LinkedHashMap<>();
        for (ZoomosParsingStats s : baselineStatsList) {
            String key = s.getSiteName() + "|" + (s.getCityName() != null ? s.getCityName() : "")
                    + "|" + (s.getAddressId() != null ? s.getAddressId() : "");
            baselineByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        Map<String, List<ZoomosParsingStats>> bySiteCity = stats.stream()
                .collect(Collectors.groupingBy(s -> {
                    String key = s.getSiteName() + "|" + (s.getCityName() != null ? s.getCityName() : "");
                    if (s.getAddressId() != null && !s.getAddressId().isBlank()) key += "|" + s.getAddressId();
                    return key;
                }));

        List<Map<String, Object>> issues = new ArrayList<>();

        List<ZoomosCityId> allCityIds = parserService.getCityIds(run.getShop().getId())
                .stream().filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .collect(Collectors.toList());
        Map<String, ZoomosCityId> cityIdBySite = allCityIds.stream()
                .filter(c -> c.getParserInclude() != null && !c.getParserInclude().isBlank())
                .collect(Collectors.toMap(ZoomosCityId::getSiteName, c -> c, (a, b) -> a));

        // Evaluate siteCityStatuses
        Map<String, String> siteCityStatuses = new LinkedHashMap<>();
        for (Map.Entry<String, List<ZoomosParsingStats>> entry : bySiteCity.entrySet()) {
            List<ZoomosParsingStats> group = new ArrayList<>(entry.getValue());
            group.sort(Comparator.comparing(s -> s.getStartTime() != null ? s.getStartTime() : ZonedDateTime.now(),
                    Comparator.naturalOrder()));
            String groupKey = entry.getKey();
            String siteName = groupKey.split("\\|")[0];
            ZoomosCityId cidConfig = cityIdBySite.get(siteName);
            String status;
            if (cidConfig != null) {
                List<ZoomosParsingStats> groupExt = new ArrayList<>(group);
                final String siteNameForFilter = siteName;
                inProgressStats.stream().filter(ip -> siteNameForFilter.equals(ip.getSiteName())).forEach(groupExt::add);
                List<Map<String, Object>> incomplete = checkService.checkParserCompleteness(
                        groupExt, cidConfig.getParserInclude(), cidConfig.getParserIncludeMode());
                status = (incomplete == null || incomplete.isEmpty()) ? "OK" : "ERROR";
            } else {
                boolean ignoreStock = group.get(0).getSiteName() != null
                        && ignoreStockSites.contains(group.get(0).getSiteName());
                ZoomosCheckService.MedianStats groupBaseline = null;
                if (baselineDatesFrom != null) {
                    String siteForBl = group.get(0).getSiteName();
                    String cityForBl = group.get(0).getCityName();
                    String addrForBl = group.get(0).getAddressId();
                    String blKey = siteForBl + "|" + (cityForBl != null ? cityForBl : "")
                            + "|" + (addrForBl != null ? addrForBl : "");
                    groupBaseline = checkService.computeBaselineMedian(
                            baselineByKey.getOrDefault(blKey, Collections.emptyList()));
                }
                status = checkService.evaluateGroup(group, dropThreshold, errorGrowthThreshold,
                        minAbsoluteErrors, ignoreStock, groupBaseline);
                if (!"OK".equals(status)) {
                    buildGroupIssues(group, status, dropThreshold, errorGrowthThreshold,
                            minAbsoluteErrors, ignoreStock, groupBaseline, issues, run.getShop().getShopName());
                }
            }
            siteCityStatuses.put(groupKey, status);
        }

        // Batch pre-load NOT_FOUND
        String[] expectedSiteNames = allCityIds.stream().map(ZoomosCityId::getSiteName)
                .distinct().toArray(String[]::new);
        Set<String> allExpectedAddrIds = new HashSet<>();
        for (ZoomosCityId cid : allCityIds) {
            allExpectedAddrIds.addAll(ZoomosCheckService.flattenAddressIds(
                    ZoomosCheckService.parseAddressMapping(cid.getAddressIds())));
        }
        Map<String, ZoomosParsingStats> lastFinishedByAddrKey = new HashMap<>();
        if (expectedSiteNames.length > 0 && !allExpectedAddrIds.isEmpty()) {
            parsingStatsRepository.findLatestFinishedBySiteAndAddressIds(
                    expectedSiteNames, allExpectedAddrIds.toArray(String[]::new))
                    .forEach(s -> lastFinishedByAddrKey.put(s.getSiteName() + "|" + s.getAddressId(), s));
        }
        Map<String, ZoomosParsingStats> lastFinishedByCityBatchKey = new HashMap<>();
        if (expectedSiteNames.length > 0) {
            parsingStatsRepository.findLatestFinishedBySites(expectedSiteNames)
                    .forEach(s -> {
                        String cId = ZoomosCheckService.extractCityId(s.getCityName());
                        if (cId != null) lastFinishedByCityBatchKey.putIfAbsent(s.getSiteName() + "|" + cId, s);
                    });
        }

        // Collect NOT_FOUND issues (same logic as checkResults)
        Set<String> foundCityKeys = new HashSet<>();
        Set<String> foundAddressKeys = new HashSet<>();
        Map<String, String> addressToCityFromData = new HashMap<>();
        for (ZoomosParsingStats s : stats) {
            String cId = ZoomosCheckService.extractCityId(s.getCityName());
            if (cId != null) foundCityKeys.add(s.getSiteName() + "|" + cId);
            if (s.getAddressId() != null && !s.getAddressId().isBlank()) {
                foundAddressKeys.add(s.getSiteName() + "|" + s.getAddressId());
                if (cId != null) addressToCityFromData.put(s.getSiteName() + "|" + s.getAddressId(), cId);
            }
        }
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
            Set<String> addressCoveredCities = new HashSet<>();
            Map<String, String> addrToCityResolved = new HashMap<>();
            for (Map.Entry<String, Set<String>> addrEntry : addrMapping.entrySet()) {
                String mappedCity = addrEntry.getKey();
                for (String aid : addrEntry.getValue()) {
                    String resolvedCity = !mappedCity.isEmpty() ? mappedCity
                            : addressToCityFromData.get(site + "|" + aid);
                    if (resolvedCity != null) {
                        addrToCityResolved.put(aid, resolvedCity);
                        addressCoveredCities.add(resolvedCity);
                    }
                }
            }
            Set<String> allExpectedAddresses = ZoomosCheckService.flattenAddressIds(addrMapping);
            for (String aid : allExpectedAddresses) {
                if (foundAddressKeys.contains(site + "|" + aid)) continue;
                String addrCity = addrToCityResolved.get(aid);
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("site", site); issue.put("city", addrCity != null ? addrCity : "");
                issue.put("cityId", addrCity != null ? addrCity : "");
                issue.put("addressId", aid);
                issue.put("checkType", cid.getCheckType()); issue.put("shopName", run.getShop().getShopName());
                ZoomosParsingStats ip = inProgressByAddrKey.get(site + "|" + aid);
                if (ip == null && addrCity != null) ip = inProgressByCityKey.get(site + "|" + addrCity);
                ZoomosParsingStats lastKnownAddr = ip == null ? lastFinishedByAddrKey.get(site + "|" + aid) : null;
                addIssueStatus(issue, ip, lastKnownAddr);
                if (ip == null && addrCity != null) {
                    parsingStatsRepository.findLatestInProgressBySiteAndCityId(site, addrCity)
                            .filter(curIp -> curIp.getCompletionPercent() == null || curIp.getCompletionPercent() < 100)
                            .ifPresent(curIp -> putCurrentInProgress(issue, curIp));
                }
                issues.add(issue);
            }
            for (String cityId : expectedCities) {
                if (addressCoveredCities.contains(cityId)) continue;
                if (foundCityKeys.contains(site + "|" + cityId)) continue;
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("site", site); issue.put("city", cityId); issue.put("cityId", cityId);
                issue.put("checkType", cid.getCheckType()); issue.put("shopName", run.getShop().getShopName());
                ZoomosParsingStats ip = inProgressByCityKey.get(site + "|" + cityId);
                ZoomosParsingStats lastKnownCity = ip == null ? lastFinishedByCityBatchKey.get(site + "|" + cityId) : null;
                addIssueStatus(issue, ip, lastKnownCity);
                if (ip == null) {
                    parsingStatsRepository.findLatestInProgressBySiteAndCityId(site, cityId)
                            .filter(curIp -> curIp.getCompletionPercent() == null || curIp.getCompletionPercent() < 100)
                            .ifPresent(curIp -> putCurrentInProgress(issue, curIp));
                }
                issues.add(issue);
            }
        }

        List<Map<String, Object>> groups = buildGroups(stats, inProgressStats, cityIdBySite,
                siteCityStatuses, issues, prioritySiteNames, baselineStatsList);

        // Baseline dates for separator in table
        if (baselineDays > 0) {
            model.addAttribute("baselineFrom",
                    run.getDateFrom().minusDays(baselineDays).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            model.addAttribute("baselineTo",
                    run.getDateFrom().minusDays(1).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        }

        Map<String, String> parserIncludeBysite = new HashMap<>();
        cityIdBySite.forEach((site, cid) -> parserIncludeBysite.put(site, cid.getParserInclude()));

        model.addAttribute("run", run);
        model.addAttribute("groups", groups);
        model.addAttribute("baseUrl", zoomosConfig.getBaseUrl());
        model.addAttribute("parserIncludeBysite", parserIncludeBysite);
        model.addAttribute("prioritySiteNames", prioritySiteNames);
        model.addAttribute("redmineEnabled", redmineService.isEnabled());
        if (redmineService.isEnabled()) {
            Set<String> allSiteNames = groups.stream()
                    .map(g -> (String) g.get("siteName"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!allSiteNames.isEmpty()) {
                List<com.java.model.entity.ZoomosRedmineIssue> existing =
                        redmineService.findAllBySiteNames(allSiteNames);
                model.addAttribute("redmineIssues",
                        existing.stream().collect(Collectors.toMap(
                                com.java.model.entity.ZoomosRedmineIssue::getSiteName, e -> e)));
            } else {
                model.addAttribute("redmineIssues", Collections.emptyMap());
            }
        } else {
            model.addAttribute("redmineIssues", Collections.emptyMap());
        }

        return "zoomos/check-results-groups :: groupsBlock";
    }

    private void buildGroupIssues(List<ZoomosParsingStats> sortedAsc, String groupStatus,
                                    int dropThreshold, int errorGrowthThreshold,
                                    int minAbsoluteErrors, boolean ignoreStock,
                                    ZoomosCheckService.MedianStats baseline,
                                    List<Map<String, Object>> issues, String shopName) {
        int initialIssueCount = issues.size();
        ZoomosParsingStats newest = sortedAsc.get(sortedAsc.size() - 1);
        ZoomosParsingStats prev   = sortedAsc.size() >= 2 ? sortedAsc.get(sortedAsc.size() - 2) : null;

        String siteName    = newest.getSiteName();
        String cityName    = newest.getCityName();
        String checkType   = newest.getCheckType();
        String addressId   = newest.getAddressId();
        String addressName = newest.getAddressName();
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

        // WARNING (одиночная запись без baseline): товары есть, но в наличии 0 — нет с чем сравнить
        if (!ignoreStock && prev == null && baseline == null
                && newest.getInStock() != null && newest.getInStock() == 0
                && newest.getTotalProducts() != null && newest.getTotalProducts() > 0) {
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
            issue.put("addressId", addressId); issue.put("addressName", addressName);
            issue.put("checkType", checkType); issue.put("shopName", shopName);
            issue.put("type", "WARNING");
            issue.put("message", "В наличии: 0 из " + newest.getTotalProducts() + " товаров (первая запись)");
            issues.add(issue);
        }

        if (prev == null && baseline == null) return;

        boolean alwaysZeroInStock = sortedAsc.stream()
                .allMatch(s -> s.getInStock() == null || s.getInStock() == 0);

        // --- inStock-анализ (первичная метрика) ---
        if (!ignoreStock && !alwaysZeroInStock) {
            // Референс: медиана или предыдущая запись
            Integer refInStock = (baseline != null && baseline.inStock() != null)
                    ? baseline.inStock()
                    : (prev != null ? prev.getInStock() : null);
            boolean usingBaseline = baseline != null && baseline.inStock() != null;
            Integer newStock = newest.getInStock();

            if (refInStock != null && refInStock > 0 && newStock != null) {
                if (newStock == 0) {
                    // Ищем последнее ненулевое значение для сообщения
                    int lastNonZero = usingBaseline ? refInStock
                            : sortedAsc.subList(0, sortedAsc.size() - 1).stream()
                                    .filter(s -> s.getInStock() != null && s.getInStock() > 0)
                                    .mapToInt(ZoomosParsingStats::getInStock).max().orElse(refInStock);
                    String prefix = usingBaseline ? String.format("[медиана: %d]", refInStock) : String.valueOf(lastNonZero);
                    Map<String, Object> issue = new LinkedHashMap<>();
                    issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
                    issue.put("addressId", addressId); issue.put("addressName", addressName);
                    issue.put("checkType", checkType); issue.put("shopName", shopName);
                    issue.put("type", "ERROR");
                    issue.put("message", String.format("В наличии: %s → 0 (−100%%)", prefix));
                    issues.add(issue);
                } else {
                    double drop = (double)(refInStock - newStock) / refInStock * 100;
                    if (drop > dropThreshold) {
                        String refLabel = usingBaseline ? String.format("[медиана: %d]", refInStock) : String.valueOf(refInStock);
                        Map<String, Object> issue = new LinkedHashMap<>();
                        issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
                        issue.put("addressId", addressId); issue.put("addressName", addressName);
                        issue.put("checkType", checkType); issue.put("shopName", shopName);
                        issue.put("type", "ERROR");
                        issue.put("message", String.format("Падение 'В наличии': %s → %d (−%.0f%%)", refLabel, newStock, drop));
                        issues.add(issue);
                    }
                }
            }
            // alwaysZeroInStock=false но конкретной записи нет — предупреждаем только если есть товары
        } else if (!ignoreStock && alwaysZeroInStock) {
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

        // --- totalProducts (запасная метрика — только если inStock недоступен) ---
        if (ignoreStock || alwaysZeroInStock) {
            Integer refTotal = (baseline != null && baseline.totalProducts() != null)
                    ? baseline.totalProducts()
                    : (prev != null ? prev.getTotalProducts() : null);
            boolean usingBaseline = baseline != null && baseline.totalProducts() != null;
            Integer newTotal = newest.getTotalProducts();
            if (refTotal != null && refTotal > 0 && newTotal != null) {
                double drop = (double)(refTotal - newTotal) / refTotal * 100;
                if (drop > dropThreshold) {
                    String refLabel = usingBaseline ? String.format("[медиана: %d]", refTotal) : String.valueOf(refTotal);
                    Map<String, Object> issue = new LinkedHashMap<>();
                    issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
                    issue.put("addressId", addressId); issue.put("addressName", addressName);
                    issue.put("checkType", checkType); issue.put("shopName", shopName);
                    issue.put("type", "ERROR");
                    issue.put("message", String.format("Падение товаров: %s → %d (−%.0f%%)", refLabel, newTotal, drop));
                    issues.add(issue);
                }
            }

            // Ошибки парсинга — только когда inStock недоступен
            int prevErr = prev != null && prev.getErrorCount() != null ? prev.getErrorCount() : 0;
            int newErr  = newest.getErrorCount() != null ? newest.getErrorCount() : 0;
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
        }

        // Медленная выкачка (только если других проблем не выявлено)
        if (issues.size() == initialIssueCount
                && baseline != null && baseline.durationMinutes() != null
                && newest.getParsingDurationMinutes() != null) {
            int refDur = baseline.durationMinutes();
            int curDur = newest.getParsingDurationMinutes();
            if (refDur > 0 && (double) curDur > refDur * 1.5) {
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("site", siteName); issue.put("city", cityName); issue.put("cityId", cityId);
                issue.put("addressId", addressId); issue.put("addressName", addressName);
                issue.put("checkType", checkType); issue.put("shopName", shopName);
                issue.put("type", "WARNING");
                issue.put("message", String.format("Медленная выкачка: %d мин (базовый: %d мин)", curDur, refDur));
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
        issue.put("noData", true); // маркер: нет данных (бывший NOT_FOUND/IN_PROGRESS)
        if (ip == null) {
            issue.put("type", "ERROR");
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
            issue.put("type", "WARNING"); // IN_PROGRESS и frozen → WARNING (не блокирует canDeliver)
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
            case "ERROR" -> 0;
            case "WARNING" -> 1;
            default -> 2; // OK
        };
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildGroups(
            List<ZoomosParsingStats> stats,
            List<ZoomosParsingStats> inProgressStats,
            Map<String, ZoomosCityId> cityIdBySite,
            Map<String, String> siteCityStatuses,
            List<Map<String, Object>> issues,
            Set<String> prioritySiteNames,
            List<ZoomosParsingStats> baselineStatsList) {

        // Группируем по сайту (верхний уровень)
        Map<String, List<ZoomosParsingStats>> bySite = stats.stream()
                .collect(Collectors.groupingBy(ZoomosParsingStats::getSiteName));
        // Для сайтов с парсер-фильтром: добавляем незавершённые выкачки в таблицу,
        // только если для сайта уже есть завершённые данные (чтобы не создавать дублей)
        for (ZoomosParsingStats ip : inProgressStats) {
            if (ip.getSiteName() != null
                    && cityIdBySite.containsKey(ip.getSiteName())
                    && bySite.containsKey(ip.getSiteName())) {
                bySite.get(ip.getSiteName()).add(ip);
            }
        }

        List<Map<String, Object>> groups = new ArrayList<>();

        bySite.forEach((siteName, siteStats) -> {
            String worstStatus = siteStats.stream()
                    .map(s -> {
                        String key = s.getSiteName() + "|" + (s.getCityName() != null ? s.getCityName() : "");
                        if (s.getAddressId() != null && !s.getAddressId().isBlank()) key += "|" + s.getAddressId();
                        return siteCityStatuses.getOrDefault(key, "OK");
                    })
                    .min(Comparator.comparingInt(ZoomosAnalysisController::statusPriority))
                    .orElse("OK");

            String checkType = siteStats.get(0).getCheckType();

            Map<String, List<ZoomosParsingStats>> byCity = siteStats.stream()
                    .collect(Collectors.groupingBy(
                            s -> s.getCityName() != null ? s.getCityName() : "",
                            LinkedHashMap::new, Collectors.toList()));

            List<Map<String, Object>> cityGroups = new ArrayList<>();
            byCity.forEach((cityName, cityStats) -> {
                cityStats.sort(Comparator.comparing(
                        (ZoomosParsingStats s) -> s.getStartTime() != null ? s.getStartTime() : ZonedDateTime.now())
                        .reversed());

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
                addressGroups.sort(Comparator.comparingInt(ag -> statusPriority((String) ag.get("status"))));

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
                List<String> cityIssueMessages = issues.stream()
                        .filter(iss -> siteName.equals(iss.get("site")) && cityName.equals(iss.get("city")))
                        .map(iss -> (String) iss.get("message"))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!cityIssueMessages.isEmpty()) {
                    cg.put("issueMessages", cityIssueMessages);
                }
                cityGroups.add(cg);
            });
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

        groups.sort(Comparator.comparingInt((Map<String, Object> g) -> Boolean.TRUE.equals(g.get("isPriority")) ? 0 : 1)
                .thenComparingInt(g -> statusPriority((String) g.get("status")))
                .thenComparing(g -> (String) g.get("siteName")));

        // Добавляем noData-пары для отображения в Блоке 3
        Map<String, Map<String, Object>> groupBySite = new LinkedHashMap<>();
        for (Map<String, Object> g : groups) {
            groupBySite.put((String) g.get("siteName"), g);
        }
        for (Map<String, Object> issue : issues) {
            if (!Boolean.TRUE.equals(issue.get("noData"))) continue;
            String iType     = (String) issue.get("type");
            String iSite     = (String) issue.get("site");
            String iCity     = (String) issue.get("city");
            String iCityId   = (String) issue.get("cityId");
            String iAddrId   = (String) issue.get("addressId");
            String iAddrName = (String) issue.get("addressName");
            String iMsg      = (String) issue.get("message");
            String iCheckType = (String) issue.get("checkType");

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
                ag.put("noData", true);
                cityGroupAddrGroups.add(ag);
            }
        }

        // Пересортировать все уровни с учётом новых записей
        for (Map<String, Object> g : groups) {
            List<Map<String, Object>> cgs = (List<Map<String, Object>>) g.get("cityGroups");
            for (Map<String, Object> cg : cgs) {
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

        // Прикрепляем baseline-записи к таблицам деталей
        if (!baselineStatsList.isEmpty()) {
            Map<String, List<ZoomosParsingStats>> blBySca = new LinkedHashMap<>();
            for (ZoomosParsingStats s : baselineStatsList) {
                String key = s.getSiteName() + "|"
                        + (s.getCityName() != null ? s.getCityName() : "") + "|"
                        + (s.getAddressId() != null ? s.getAddressId() : "");
                blBySca.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
            }
            for (Map<String, Object> g : groups) {
                String sn = (String) g.get("siteName");
                List<Map<String, Object>> cgs = (List<Map<String, Object>>) g.get("cityGroups");
                for (Map<String, Object> cg : cgs) {
                    String cn = cg.get("cityName") != null ? (String) cg.get("cityName") : "";
                    List<Map<String, Object>> ags = (List<Map<String, Object>>) cg.get("addressGroups");
                    for (Map<String, Object> ag : ags) {
                        String aid = ag.get("addressId") != null ? (String) ag.get("addressId") : "";
                        List<ZoomosParsingStats> bl = blBySca.get(sn + "|" + cn + "|" + aid);
                        if (bl != null && !bl.isEmpty()) {
                            List<ZoomosParsingStats> agStats = (List<ZoomosParsingStats>) ag.get("stats");
                            agStats.addAll(bl);
                        }
                    }
                }
            }
        }

        return groups;
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
    public String checkHistory(@RequestParam(required = false) String shop, Model model) {
        List<ZoomosCheckRun> runs = checkRunRepository.findAll(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "startedAt"));
        model.addAttribute("runs", runs);
        model.addAttribute("shopFilter", shop != null ? shop : "");
        return "zoomos/check-history";
    }

    @PostMapping("/check/history/delete")
    @Transactional
    public String deleteCheckHistory(@RequestParam List<Long> ids,
                                     org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        for (Long id : ids) {
            parsingStatsRepository.deleteByCheckRunId(id);
            checkRunRepository.deleteById(id);
        }
        ra.addFlashAttribute("success", "Удалено записей: " + ids.size());
        return "redirect:/zoomos/check/history";
    }

    // =========================================================================
    // Страница проверок по клиентам /zoomos/clients
    // =========================================================================

    @GetMapping("/clients")
    public String clientsCheckPage(Model model) {
        List<ZoomosShop> shops = shopRepository.findAllByClientIsNotNullWithClient().stream()
                .filter(ZoomosShop::isEnabled)
                .sorted(Comparator.comparing(s -> s.getClient().getName()))
                .collect(Collectors.toList());

        Map<Long, ZoomosShopSchedule> schedulesMap = new LinkedHashMap<>();
        Map<Long, Long> lastRunIds = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        Map<Long, String> lastRunFormatted = new LinkedHashMap<>();

        for (ZoomosShop shop : shops) {
            scheduleRepository.findFirstByShopId(shop.getId())
                    .ifPresent(s -> schedulesMap.put(shop.getId(), s));
            checkRunRepository.findFirstByShopIdOrderByStartedAtDesc(shop.getId()).ifPresent(run -> {
                lastRunIds.put(shop.getId(), run.getId());
                if (run.getStartedAt() != null) {
                    lastRunFormatted.put(shop.getId(), run.getStartedAt().format(fmt));
                }
            });
        }

        Map<String, String> dt = settingsService.getAllSettings();
        model.addAttribute("defDropThreshold", dt.getOrDefault("default.drop_threshold", "10"));
        model.addAttribute("defErrThreshold",  dt.getOrDefault("default.error_growth_threshold", "30"));
        model.addAttribute("defBaselineDays",  dt.getOrDefault("default.baseline_days", "7"));
        model.addAttribute("defMinAbsErrors",  dt.getOrDefault("default.min_absolute_errors", "5"));
        model.addAttribute("defTrendDrop",     dt.getOrDefault("default.trend_drop_threshold", "30"));
        model.addAttribute("defTrendErr",      dt.getOrDefault("default.trend_error_threshold", "100"));

        model.addAttribute("shops", shops);
        model.addAttribute("schedulesMap", schedulesMap);
        model.addAttribute("lastRunIds", lastRunIds);
        model.addAttribute("lastRunFormatted", lastRunFormatted);
        return "zoomos/clients";
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
        // Ключ — scheduleId, для lastRunFormatted и lastRunIds
        Map<Long, String> lastRunFormatted = new LinkedHashMap<>();
        Map<Long, Long> lastRunIds = new LinkedHashMap<>();
        // Ключ — shopId → список расписаний
        Map<Long, List<ZoomosShopSchedule>> schedules = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        for (ZoomosShop shop : shops) {
            List<ZoomosShopSchedule> list = scheduleRepository.findAllByShopId(shop.getId());
            schedules.put(shop.getId(), list);
            // lastRunIds: последний run магазина для ссылки на результаты (общий для всех расписаний)
            checkRunRepository.findFirstByShopIdOrderByStartedAtDesc(shop.getId())
                    .ifPresent(run -> list.forEach(s -> lastRunIds.put(s.getId(), run.getId())));
            // lastRunFormatted: время последнего запуска конкретного расписания (из sched.lastRunAt)
            for (ZoomosShopSchedule s : list) {
                if (s.getLastRunAt() != null) {
                    lastRunFormatted.put(s.getId(),
                            s.getLastRunAt().withZoneSameInstant(java.time.ZoneId.systemDefault()).format(fmt));
                }
            }
        }
        model.addAttribute("shops", shops);
        model.addAttribute("schedules", schedules);
        model.addAttribute("lastRunFormatted", lastRunFormatted);
        model.addAttribute("lastRunIds", lastRunIds);
        return "zoomos/schedule";
    }

    /** Создать новое расписание для магазина с дефолтными значениями из zoomos_settings */
    @PostMapping("/schedule/{shopId}/new")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createSchedule(@PathVariable Long shopId) {
        ZoomosShopSchedule s = ZoomosShopSchedule.builder()
                .shopId(shopId)
                .dropThreshold(settingsService.getInt("default.drop_threshold", 10))
                .errorGrowthThreshold(settingsService.getInt("default.error_growth_threshold", 30))
                .baselineDays(settingsService.getInt("default.baseline_days", 7))
                .minAbsoluteErrors(settingsService.getInt("default.min_absolute_errors", 5))
                .trendDropThreshold(settingsService.getInt("default.trend_drop_threshold", 30))
                .trendErrorThreshold(settingsService.getInt("default.trend_error_threshold", 100))
                .build();
        schedulerService.saveAndReschedule(s);
        return ResponseEntity.ok(Map.of("success", true, "scheduleId", s.getId()));
    }

    /** Сохранить расписание по scheduleId */
    @PostMapping("/schedule/item/{scheduleId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveScheduleItem(
            @PathVariable Long scheduleId,
            @RequestParam(defaultValue = "0 0 8 * * *") String cronExpression,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) String timeFrom,
            @RequestParam(required = false) String timeTo,
            @RequestParam(defaultValue = "10") int dropThreshold,
            @RequestParam(defaultValue = "30") int errorGrowthThreshold,
            @RequestParam(defaultValue = "7") int baselineDays,
            @RequestParam(defaultValue = "5") int minAbsoluteErrors,
            @RequestParam(defaultValue = "-1") int dateOffsetFrom,
            @RequestParam(defaultValue = "0") int dateOffsetTo,
            @RequestParam(defaultValue = "30") int trendDropThreshold,
            @RequestParam(defaultValue = "100") int trendErrorThreshold) {
        return scheduleRepository.findById(scheduleId).map(schedule -> {
            schedule.setLabel(label != null && !label.isBlank() ? label.trim() : null);
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
            return ResponseEntity.ok(Map.<String, Object>of("success", true));
        }).orElse(ResponseEntity.badRequest().body(Map.of("success", false, "error", "Расписание не найдено")));
    }

    /** Переключить включение одного расписания по scheduleId */
    @PostMapping("/schedule/item/{scheduleId}/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleScheduleItem(@PathVariable Long scheduleId) {
        schedulerService.toggleEnabledById(scheduleId);
        boolean isEnabled = scheduleRepository.findById(scheduleId)
                .map(ZoomosShopSchedule::isEnabled).orElse(false);
        return ResponseEntity.ok(Map.of("success", true, "isEnabled", isEnabled));
    }

    /** Удалить одно расписание по scheduleId */
    @PostMapping("/schedule/item/{scheduleId}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteScheduleItem(@PathVariable Long scheduleId) {
        schedulerService.deleteScheduleById(scheduleId);
        return ResponseEntity.ok(Map.of("success", true));
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
                    String city = group.get(0).getCityName();
                    String briefMsg = buildBriefAlertMessage(group, status, drop, errGrowth, minAbsErr, ignoreStk);
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("city", city != null ? city : "");
                    detail.put("message", briefMsg);
                    detail.put("runId", run.getId());
                    mergePriorityAlert(siteAlerts, canonical, status, detail);
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
                    Map<String, Object> notFoundDetail = new LinkedHashMap<>();
                    notFoundDetail.put("city", "");
                    notFoundDetail.put("message", "Нет данных в выкачке");
                    notFoundDetail.put("runId", run.getId());
                    mergePriorityAlert(siteAlerts, canonical, "NOT_FOUND", notFoundDetail);
                }
            }

            return ResponseEntity.ok(new ArrayList<>(siteAlerts.values()));
        } catch (Exception e) {
            log.error("Ошибка priority-alerts: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    private void mergePriorityAlert(Map<String, Map<String, Object>> siteAlerts,
                                    String siteName, String status, Map<String, Object> issueDetail) {
        siteAlerts.compute(siteName, (k, existing) -> {
            if (existing == null) {
                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("siteName", siteName);
                alert.put("severity", status);
                alert.put("issueCount", 1);
                List<Map<String, Object>> issues = new ArrayList<>();
                if (issueDetail != null) issues.add(issueDetail);
                alert.put("issues", issues);
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
                if (issueDetail != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> issues = (List<Map<String, Object>>) existing.get("issues");
                    issues.add(issueDetail);
                }
                return existing;
            }
        });
    }

    private String buildBriefAlertMessage(List<ZoomosParsingStats> sortedAsc, String status,
                                          int dropThreshold, int errGrowthThreshold,
                                          int minAbsoluteErrors, boolean ignoreStock) {
        ZoomosParsingStats newest = sortedAsc.get(sortedAsc.size() - 1);
        ZoomosParsingStats prev = sortedAsc.size() >= 2 ? sortedAsc.get(sortedAsc.size() - 2) : null;

        boolean alwaysZeroInStock = sortedAsc.stream()
                .allMatch(s -> s.getInStock() == null || s.getInStock() == 0);

        if (!ignoreStock && !alwaysZeroInStock) {
            Integer refInStock = prev != null ? prev.getInStock() : null;
            Integer newStock = newest.getInStock();
            if (refInStock != null && refInStock > 0 && newStock != null) {
                if (newStock == 0) {
                    return String.format("В наличии: %d → 0 (−100%%)", refInStock);
                }
                double drop = (double)(refInStock - newStock) / refInStock * 100;
                if (drop > dropThreshold) {
                    return String.format("В наличии: %d → %d (−%.0f%%)", refInStock, newStock, drop);
                }
            }
        }

        if (ignoreStock || alwaysZeroInStock) {
            Integer refTotal = prev != null ? prev.getTotalProducts() : null;
            Integer newTotal = newest.getTotalProducts();
            if (refTotal != null && refTotal > 0 && newTotal != null) {
                double drop = (double)(refTotal - newTotal) / refTotal * 100;
                if (drop > dropThreshold) {
                    return String.format("Падение товаров: %d → %d (−%.0f%%)", refTotal, newTotal, drop);
                }
            }
            int prevErr = prev != null && prev.getErrorCount() != null ? prev.getErrorCount() : 0;
            int newErr = newest.getErrorCount() != null ? newest.getErrorCount() : 0;
            if (newErr >= minAbsoluteErrors) {
                if (prevErr > 0 && newErr > prevErr) {
                    double growth = (double)(newErr - prevErr) / prevErr * 100;
                    if (growth > errGrowthThreshold) {
                        return String.format("Рост ошибок: %d → %d (+%.0f%%)", prevErr, newErr, growth);
                    }
                } else if (prevErr == 0) {
                    return String.format("Ошибки парсинга: 0 → %d", newErr);
                }
            }
        }

        if (newest.getCompletionPercent() != null && newest.getCompletionPercent() < 100) {
            return String.format("Выкачка: %.0f%%", newest.getCompletionPercent());
        }
        return "ERROR".equals(status) ? "Проблема с выкачкой" : "Нужна проверка";
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

}
