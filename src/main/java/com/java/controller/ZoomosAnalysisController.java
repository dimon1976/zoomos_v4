package com.java.controller;

import com.java.config.ZoomosConfig;
import com.java.dto.zoomos.ZoomosCheckParams;
import com.java.dto.zoomos.ZoomosSiteResult;
import com.java.model.entity.*;
import com.java.service.ZoomosAnalysisService;
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
import com.java.dto.zoomos.SchedulePageDto;
import com.java.service.ZoomosViewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
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
import java.util.Collections;
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
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private final ZoomosViewService zoomosViewService;
    private final ZoomosAnalysisService analysisService;

    @Qualifier("zoomosCheckExecutor")
    private final java.util.concurrent.Executor zoomosCheckExecutor;

    @GetMapping({"", "/"})
    public String index(Model model) {
        List<ZoomosShop> allShops = parserService.getAllShops();
        List<ZoomosShop> shops = allShops.stream().filter(ZoomosShop::isEnabled)
                .sorted(Comparator.comparing(ZoomosShop::isPriority).reversed())
                .collect(Collectors.toList());
        List<ZoomosShop> disabledShops = allShops.stream().filter(s -> !s.isEnabled()).collect(Collectors.toList());

        List<Long> allShopIds = allShops.stream().map(ZoomosShop::getId).collect(Collectors.toList());

        // Batch pre-load cityIds и расписания одним запросом на всё
        Map<Long, List<ZoomosCityId>> cityIdsMap = new java.util.LinkedHashMap<>();
        cityIdRepository.findByShopIdInOrderBySiteName(allShopIds).stream()
                .collect(Collectors.groupingBy(c -> c.getShop().getId()))
                .forEach(cityIdsMap::put);
        allShops.forEach(shop -> cityIdsMap.putIfAbsent(shop.getId(), List.of()));

        Map<String, String> cityNamesMap = cityNameRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ZoomosCityName::getCityId, ZoomosCityName::getCityName));

        // Расписания для badge'ей вкл/выкл на каждой карточке (batch загрузка).
        // Если у магазина несколько расписаний, берём включённое (если есть), иначе любое первое.
        Map<Long, ZoomosShopSchedule> schedulesMap = new java.util.LinkedHashMap<>();
        scheduleRepository.findAllByShopIdIn(allShopIds).stream()
                .collect(Collectors.groupingBy(ZoomosShopSchedule::getShopId))
                .forEach((shopId, scheds) -> {
                    ZoomosShopSchedule representative = scheds.stream()
                            .filter(ZoomosShopSchedule::isEnabled).findFirst()
                            .orElse(scheds.get(0));
                    schedulesMap.put(shopId, representative);
                });

        // Приоритетные сайты для иконок в таблице сайтов клиента
        Set<String> prioritySiteNames = knownSiteRepository.findAllByIsPriorityTrue().stream()
                .map(ZoomosKnownSite::getSiteName).collect(Collectors.toSet());

        // Site-level master city (Task 2) для отображения в index
        List<ZoomosKnownSite> allKnownSites = knownSiteRepository.findAll();
        Map<String, Long> knownSiteIdMap = allKnownSites.stream()
                .collect(Collectors.toMap(ZoomosKnownSite::getSiteName, ZoomosKnownSite::getId, (a, b) -> a));
        Map<String, String> siteMasterCityMap = allKnownSites.stream()
                .filter(s -> s.getMasterCityId() != null && !s.getMasterCityId().isBlank())
                .collect(Collectors.toMap(ZoomosKnownSite::getSiteName, ZoomosKnownSite::getMasterCityId));

        model.addAttribute("shops", shops);
        model.addAttribute("disabledShops", disabledShops);
        model.addAttribute("cityIdsMap", cityIdsMap);
        model.addAttribute("cityNamesMap", cityNamesMap);
        model.addAttribute("schedulesMap", schedulesMap);
        model.addAttribute("prioritySiteNames", prioritySiteNames);
        model.addAttribute("knownSiteIdMap", knownSiteIdMap);
        model.addAttribute("siteMasterCityMap", siteMasterCityMap);
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
            @RequestParam(required = false) String cityIds,
            @RequestParam(required = false) String siteName) {
        // PERF-003: без фильтра возвращаем пустой список вместо полной загрузки таблицы
        List<com.java.model.entity.ZoomosCityAddress> addrs;
        if (cityIds != null && !cityIds.isBlank()) {
            List<String> ids = Arrays.asList(cityIds.split(","));
            addrs = cityAddressRepository.findByCityIdInOrderByCityIdAscAddressIdAsc(ids);
        } else {
            addrs = List.of();
        }
        // Если передан siteName — фильтруем по адресам, настроенным для этого сайта
        if (siteName != null && !siteName.isBlank()) {
            Set<String> siteAddressIds = cityIdRepository.findAllBySiteName(siteName).stream()
                    .flatMap(e -> com.java.service.ZoomosCheckService
                            .flattenAddressIds(com.java.service.ZoomosCheckService.parseAddressMapping(e.getAddressIds()))
                            .stream())
                    .collect(java.util.stream.Collectors.toSet());
            if (!siteAddressIds.isEmpty()) {
                addrs = addrs.stream()
                        .filter(a -> siteAddressIds.contains(a.getAddressId()))
                        .collect(java.util.stream.Collectors.toList());
            }
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

    @PostMapping("/city-ids/{id}/config-issue")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateConfigIssue(@PathVariable Long id,
                                                                  @RequestParam(required = false) String type,
                                                                  @RequestParam(required = false) String note) {
        if (type != null && !type.isBlank()) {
            try { ConfigIssueType.valueOf(type.trim()); }
            catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Недопустимый тип: " + type));
            }
        }
        if (note != null && note.length() > 2000) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Заметка слишком длинная (макс. 2000 символов)"));
        }
        return cityIdRepository.findById(id).map(cityId -> {
            boolean hasIssue = type != null && !type.isBlank();
            cityId.setHasConfigIssue(hasIssue);
            cityId.setConfigIssueType(hasIssue ? type.trim() : null);
            cityId.setConfigIssueNote(hasIssue && note != null && !note.isBlank() ? note.trim() : null);
            cityIdRepository.save(cityId);
            Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("success", true);
            resp.put("hasConfigIssue", cityId.isHasConfigIssue());
            resp.put("configIssueType", cityId.getConfigIssueType() != null ? cityId.getConfigIssueType() : "");
            resp.put("configIssueNote", cityId.getConfigIssueNote() != null ? cityId.getConfigIssueNote() : "");
            return ResponseEntity.ok(resp);
        }).orElse(ResponseEntity.badRequest().body(Map.of("success", false, "error", "Запись не найдена")));
    }

    @PostMapping("/shops/{shopId}/city-ids/add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addCityId(@PathVariable Long shopId,
                                                          @RequestParam String siteName,
                                                          @RequestParam(required = false) String cityIds) {
        try {
            parserService.addCityIdManually(shopId, siteName, cityIds);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Ошибка добавления city-id для shop {}: {}", shopId, e.getMessage());
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

    @PostMapping("/city-ids/{id}/master-city")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateMasterCityId(@PathVariable Long id,
                                                                    @RequestParam(required = false) String masterCityId) {
        try {
            parserService.updateMasterCityId(id, masterCityId);
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
                    entry.setAddressIds(objectMapper.writeValueAsString(addressMapping));
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
            @RequestParam(required = false) Long scheduleId,
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
                    checkService.runCheck(ZoomosCheckParams.builder()
                            .shopId(shopId)
                            .scheduleId(scheduleId)
                            .dateFrom(from)
                            .dateTo(to)
                            .timeFrom(tf)
                            .timeTo(tt)
                            .dropThreshold(dropThreshold)
                            .errorGrowthThreshold(errorGrowthThreshold)
                            .baselineDays(bl)
                            .minAbsoluteErrors(mae)
                            .trendDropThreshold(tdt)
                            .trendErrorThreshold(tet)
                            .operationId(operationId)
                            .build());
                    // lastRunAt сохраняется внутри ZoomosCheckService.runCheck() до финального WebSocket
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


    @GetMapping("/check/results-new/{runId}")
    public String checkResultsNew(@PathVariable Long runId, Model model) {
        ZoomosCheckRun run = checkRunRepository.findByIdWithShop(runId)
                .orElseThrow(() -> new IllegalArgumentException("Проверка не найдена: " + runId));
        java.time.format.DateTimeFormatter dmFmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String dateFromFmt = run.getDateFrom() != null ? run.getDateFrom().format(dmFmt) : "";
        String dateToFmt   = run.getDateTo()   != null ? run.getDateTo().format(dmFmt)   : "";
        String startedAt   = run.getStartedAt() != null
                ? run.getStartedAt().withZoneSameInstant(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                : "";
        model.addAttribute("runId", runId);
        model.addAttribute("shopName",    run.getShop().getShopName());
        model.addAttribute("dateFromFmt", dateFromFmt);
        model.addAttribute("dateToFmt",   dateToFmt);
        model.addAttribute("startedAt",   startedAt);
        model.addAttribute("pageTitle",   run.getShop().getShopName() + " · " + dateFromFmt + " — " + dateToFmt);
        model.addAttribute("baseUrl",     zoomosConfig.getBaseUrl());
        return "zoomos/check-results-new";
    }

    @GetMapping("/check/run/{runId}/info")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> runInfo(@PathVariable Long runId) {
        return checkRunRepository.findByIdWithShop(runId).map(run -> {
            java.time.format.DateTimeFormatter dmFmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
            Map<String, Object> info = new HashMap<>();
            info.put("shopName", run.getShop().getShopName());
            info.put("dateFrom", run.getDateFrom() != null ? run.getDateFrom().format(dmFmt) : "");
            info.put("dateTo",   run.getDateTo()   != null ? run.getDateTo().format(dmFmt)   : "");
            info.put("startedAt", run.getStartedAt() != null
                    ? run.getStartedAt().withZoneSameInstant(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                    : "");
            return ResponseEntity.ok(info);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/shops/{shopId}/last-instock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> lastInStock(@PathVariable Long shopId) {
        try {
            List<ZoomosCheckRun> runs = checkRunRepository.findLastTwoCompletedRuns(shopId);
            if (runs.isEmpty()) return ResponseEntity.ok(Collections.singletonMap("inStock", null));
            ZoomosCheckRun current = runs.get(0);
            Long currentInStock = parsingStatsRepository.sumInStockByRunId(current.getId());
            Long currentSites   = parsingStatsRepository.countSitesByRunId(current.getId());
            Map<String, Object> result = new HashMap<>();
            result.put("inStock",    currentInStock);
            result.put("date",       current.getDateTo() != null ? current.getDateTo().toString() : null);
            result.put("sitesCount", currentSites);
            if (runs.size() > 1) {
                Long prevInStock = parsingStatsRepository.sumInStockByRunId(runs.get(1).getId());
                result.put("previousInStock", prevInStock);
                result.put("change", (prevInStock != null && prevInStock > 0 && currentInStock != null)
                        ? currentInStock - prevInStock : null);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("lastInStock error for shop {}: {}", shopId, e.getMessage());
            return ResponseEntity.ok(Collections.singletonMap("inStock", null));
        }
    }

    @GetMapping("/check/analyze/{runId}")
    @ResponseBody
    public ResponseEntity<List<ZoomosSiteResult>> analyzeRun(
            @PathVariable Long runId,
            @RequestParam(required = false) Long profileId,
            @RequestParam(required = false) String deadline,
            @RequestParam(defaultValue = "60") int stallMinutes) {
        try {
            ZonedDateTime deadlineTime = (deadline != null && !deadline.isBlank())
                    ? ZonedDateTime.parse(deadline) : null;
            List<ZoomosSiteResult> results = analysisService.analyze(runId, profileId, deadlineTime, stallMinutes);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Ошибка анализа run {}: {}", runId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
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
        result.put("status", run.getStatus() != null ? run.getStatus().name() : null);
        result.put("dateFrom", run.getDateFrom() != null ? run.getDateFrom().toString() : null);
        result.put("dateTo", run.getDateTo() != null ? run.getDateTo().toString() : null);
        if (run.getStartedAt() != null) {
            result.put("startedAt", run.getStartedAt()
                    .withZoneSameInstant(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        }
        if (run.getCompletedAt() != null) {
            result.put("completedAt", run.getCompletedAt()
                    .withZoneSameInstant(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/check/history")
    public String checkHistory(@RequestParam(required = false) String shop, Model model) {
        List<ZoomosCheckRun> runs = checkRunRepository.findAllWithShopOrderByStartedAtDesc(PageRequest.of(0, 500));
        model.addAttribute("runs", runs);
        model.addAttribute("shopFilter", shop != null ? shop : "");
        return "zoomos/check-history";
    }

    @PostMapping("/check/history/delete")
    public String deleteCheckHistory(@RequestParam List<Long> ids,
                                     org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        int deleted = zoomosViewService.deleteCheckRuns(ids);
        ra.addFlashAttribute("success", "Удалено записей: " + deleted);
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
            if (!name.matches("[a-z0-9._-]+")) {
                skipped.add(name + " (недопустимые символы)");
                continue;
            }
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
        int deleted = zoomosViewService.deleteKnownSite(id);
        if (deleted < 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Сайт не найден"));
        }
        return ResponseEntity.ok(Map.<String, Object>of("success", true, "deletedCityIds", deleted));
    }

    // =========================================================================
    // Расписания проверок /zoomos/schedule
    // =========================================================================

    @GetMapping("/schedule")
    public String schedulePage(Model model) {
        // ARCH-001 + PERF-001: batch-загрузка вынесена в ZoomosViewService
        // ARCH-002: типизированный DTO
        SchedulePageDto dto = zoomosViewService.buildScheduleModel();
        model.addAttribute("shops", dto.shops());
        model.addAttribute("schedules", dto.schedules());
        model.addAttribute("lastRunFormatted", dto.lastRunFormatted());
        model.addAttribute("lastRunIds", dto.lastRunIds());
        return "zoomos/schedule";
    }

    /** Последний lastRunAt для каждого расписания магазина (ключ — scheduleId). */
    @GetMapping("/schedule/last-run-times")
    @ResponseBody
    public ResponseEntity<Map<Long, String>> getScheduleLastRunTimes(@RequestParam Long shopId) {
        List<ZoomosShopSchedule> schedules = scheduleRepository.findAllByShopId(shopId);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        Map<Long, String> result = new LinkedHashMap<>();
        for (ZoomosShopSchedule s : schedules) {
            if (s.getLastRunAt() != null) {
                result.put(s.getId(),
                        s.getLastRunAt().withZoneSameInstant(java.time.ZoneId.systemDefault()).format(fmt));
            }
        }
        return ResponseEntity.ok(result);
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
        // TODO: реализовать с новой логикой статусов
        return ResponseEntity.ok(List.of());
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

    @PostMapping("/sites/{id}/master-city")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateSiteMasterCity(@PathVariable Long id,
                                                                     @RequestParam(required = false) String masterCityId) {
        return knownSiteRepository.findById(id).map(site -> {
            site.setMasterCityId(masterCityId == null || masterCityId.isBlank() ? null : masterCityId.trim());
            knownSiteRepository.save(site);
            return ResponseEntity.ok(Map.<String, Object>of("success", true));
        }).orElse(ResponseEntity.badRequest().body(Map.of("success", false, "error", "Сайт не найден")));
    }

    @PostMapping("/sites/{id}/fetch-equal-prices")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> fetchEqualPrices(@PathVariable Long id) {
        return knownSiteRepository.findById(id).map(site -> {
            try {
                Map<String, Object> result = parserService.fetchCitiesEqualPrices(site.getSiteName());
                return ResponseEntity.ok(result);
            } catch (Exception e) {
                log.warn("Ошибка fetchEqualPrices для {}: {}", site.getSiteName(), e.getMessage());
                return ResponseEntity.ok(Map.<String, Object>of("success", false, "error", e.getMessage()));
            }
        }).orElse(ResponseEntity.badRequest().body(Map.of("success", false, "error", "Сайт не найден")));
    }

    @PostMapping("/sites/fetch-equal-prices-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> fetchEqualPricesAll() {
        CompletableFuture.runAsync(() -> {
            try {
                parserService.fetchCitiesEqualPricesForAll();
            } catch (Exception e) {
                log.warn("Ошибка fetchEqualPricesForAll: {}", e.getMessage());
            }
        }, zoomosCheckExecutor);
        return ResponseEntity.ok(Map.of("success", true, "message", "Запущено в фоне"));
    }

    @GetMapping("/sites/{id}/historical-cities")
    @ResponseBody
    public ResponseEntity<List<String>> getHistoricalCities(@PathVariable Long id) {
        return knownSiteRepository.findById(id).map(site ->
                ResponseEntity.ok(parsingStatsRepository.findDistinctCityNamesBySiteName(site.getSiteName()))
        ).orElse(ResponseEntity.ok(List.of()));
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
