package com.java.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.config.ZoomosConfig;
import com.java.dto.zoomos.ZoomosCheckParams;
import com.java.model.entity.*;
import com.java.repository.*;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SameSiteAttribute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZoomosCheckService {

    private final ZoomosConfig config;
    private final ZoomosShopRepository shopRepository;
    private final ZoomosCityIdRepository cityIdRepository;
    private final ZoomosSessionRepository sessionRepository;
    private final ZoomosCheckRunRepository checkRunRepository;
    private final ZoomosParsingStatsRepository parsingStatsRepository;
    private final ZoomosCityNameRepository cityNameRepository;
    private final ZoomosCityAddressRepository cityAddressRepository;
    private final ZoomosKnownSiteRepository knownSiteRepository;
    private final ZoomosParserPatternRepository parserPatternRepository;
    private final ZoomosShopScheduleRepository scheduleRepository;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ZoomosPlaywrightHelper playwrightHelper;
    private final ZoomosAnalysisService analysisService;

    // Self-injection для REQUIRES_NEW транзакций (немедленный коммит run-записи)
    @Autowired @Lazy
    private ZoomosCheckService self;

    /**
     * Сохраняет ZoomosCheckRun в отдельной транзакции (REQUIRES_NEW) — коммитится немедленно,
     * чтобы запись с status=RUNNING была видна другим транзакциям во время проверки.
     * Используется также для финального сохранения COMPLETED до отправки WebSocket.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ZoomosCheckRun saveRunImmediate(ZoomosCheckRun run) {
        return checkRunRepository.save(run);
    }

    /**
     * Сохраняет lastRunAt расписания в отдельной транзакции (REQUIRES_NEW) — коммитится немедленно,
     * чтобы к моменту реакции JS на WebSocket данные уже были в БД.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveScheduleLastRunAt(Long scheduleId) {
        scheduleRepository.findById(scheduleId).ifPresent(s -> {
            s.setLastRunAt(ZonedDateTime.now());
            scheduleRepository.save(s);
        });
    }

    private static final DateTimeFormatter DATE_PARAM_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_PARSE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yy H:mm");
    private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd.MM");
    private static final com.fasterxml.jackson.databind.ObjectMapper STATIC_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Запуск проверки выкачки по всем активным сайтам клиента.
     */
    @Transactional
    public ZoomosCheckRun runCheck(ZoomosCheckParams params) {
        Long shopId                = params.getShopId();
        LocalDate dateFrom         = params.getDateFrom();
        LocalDate dateTo           = params.getDateTo();
        String timeFrom            = params.getTimeFrom();
        String timeTo              = params.getTimeTo();
        int dropThreshold          = params.getDropThreshold();
        int errorGrowthThreshold   = params.getErrorGrowthThreshold();
        int baselineDays           = params.getBaselineDays();
        int minAbsoluteErrors      = params.getMinAbsoluteErrors();
        int trendDropThreshold     = params.getTrendDropThreshold();
        int trendErrorThreshold    = params.getTrendErrorThreshold();
        String operationId         = params.getOperationId();

        ZoomosShop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден: " + shopId));

        List<ZoomosCityId> allCityIds = cityIdRepository.findByShopIdOrderBySiteName(shopId)
                .stream().filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .collect(Collectors.toList());

        if (allCityIds.isEmpty()) {
            throw new IllegalStateException("Нет активных сайтов для проверки у " + shop.getShopName());
        }

        // Вычисляем временной диапазон фильтрации
        LocalTime tFrom = (timeFrom != null && !timeFrom.isBlank())
                ? LocalTime.parse(timeFrom) : LocalTime.MIDNIGHT;
        LocalTime tTo   = (timeTo != null && !timeTo.isBlank())
                ? LocalTime.parse(timeTo) : LocalTime.of(23, 59);
        ZonedDateTime rangeStart = dateFrom.atTime(tFrom).atZone(java.time.ZoneOffset.UTC);
        ZonedDateTime rangeEnd   = dateTo.atTime(tTo).atZone(java.time.ZoneOffset.UTC);
        boolean hasTimeFilter = (timeFrom != null && !timeFrom.isBlank())
                || (timeTo != null && !timeTo.isBlank());

        // Создаём запись о проверке
        ZoomosCheckRun run = ZoomosCheckRun.builder()
                .shop(shop)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .timeFrom(timeFrom != null && !timeFrom.isBlank() ? timeFrom : null)
                .timeTo(timeTo != null && !timeTo.isBlank() ? timeTo : null)
                .totalSites(allCityIds.size())
                .dropThreshold(dropThreshold)
                .errorGrowthThreshold(errorGrowthThreshold)
                .baselineDays(Math.max(0, baselineDays))
                .minAbsoluteErrors(Math.max(0, minAbsoluteErrors))
                .trendDropThreshold(Math.max(1, trendDropThreshold))
                .trendErrorThreshold(Math.max(1, trendErrorThreshold))
                .build();
        // Сохраняем в REQUIRES_NEW транзакции → немедленный коммит → status=RUNNING виден в БД
        run = self.saveRunImmediate(run);
        final ZoomosCheckRun savedRun = run; // effectively final для лямбд ниже

        // Группируем по типу проверки
        Map<String, List<ZoomosCityId>> byType = allCityIds.stream()
                .collect(Collectors.groupingBy(c -> c.getCheckType() != null ? c.getCheckType() : "API"));

        // Baseline совмещается с основным парсингом: запрашиваем расширенный диапазон дат,
        // затем разделяем результаты по дате — записи до dateFrom помечаются is_baseline=true.
        final boolean combineBaseline = (run.getBaselineDays() != null && run.getBaselineDays() > 0);
        final LocalDate effectiveDateFrom = combineBaseline ? dateFrom.minusDays(run.getBaselineDays()) : dateFrom;

        List<ZoomosParsingStats> allStats = new ArrayList<>();
        List<ZoomosParsingStats> allBaselineStats = new ArrayList<>();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions().setViewportSize(1920, 1080));
            context.setDefaultTimeout(config.getTimeoutSeconds() * 1000L);

            // Авторизация
            if (!loadSession(context)) {
                login(context);
            }

            Page page = context.newPage();

            // Проверяем сессию (с retry при таймауте)
            String testUrl = config.getBaseUrl() + "/shop/" + shop.getShopName() + "/settings";
            navigateWithSessionRetry(page, context, testUrl, shop.getShopName());

            int processed = 0;
            int total = allCityIds.size();

            // --- Парсинг API-сайтов ---
            List<ZoomosCityId> apiSites = byType.getOrDefault("API", Collections.emptyList());
            // Группируем по siteName (один запрос на сайт, парсим все города)
            Map<String, List<ZoomosCityId>> apiSitesGrouped = apiSites.stream()
                    .collect(Collectors.groupingBy(ZoomosCityId::getSiteName));

            for (Map.Entry<String, List<ZoomosCityId>> entry : apiSitesGrouped.entrySet()) {
                String siteName = entry.getKey();
                List<ZoomosCityId> cityIdEntries = entry.getValue();

                sendProgress(shopId, operationId, processed, total, "Проверяем " + siteName + " (API)...");

                // Запрашиваем расширенный диапазон (включает baseline при combineBaseline=true)
                List<ZoomosParsingStats> stats = parseWithRetry(
                        () -> parseApiPage(page, siteName, effectiveDateFrom, dateTo, cityIdEntries, savedRun),
                        siteName, run);
                if (stats == null) {
                    processed += cityIdEntries.size();
                    sendProgress(shopId, operationId, processed, total, "TIMEOUT: " + siteName + " — пропущен");
                    continue;
                }
                List<ZoomosParsingStats> mainStats = splitBaseline(stats, dateFrom, combineBaseline, allBaselineStats);
                if (hasTimeFilter) {
                    mainStats = filterByTime(mainStats, rangeStart, rangeEnd);
                }
                allStats.addAll(mainStats);

                processed += cityIdEntries.size();
                sendProgress(shopId, operationId, processed, total, siteName + " — " + mainStats.size() + " записей");
            }

            // --- Парсинг ITEM-сайтов (та же parsing-history + &shop=shopName) ---
            List<ZoomosCityId> itemSites = byType.getOrDefault("ITEM", Collections.emptyList());
            Map<String, List<ZoomosCityId>> itemSitesGrouped = itemSites.stream()
                    .collect(Collectors.groupingBy(ZoomosCityId::getSiteName));

            for (Map.Entry<String, List<ZoomosCityId>> entry : itemSitesGrouped.entrySet()) {
                String siteName = entry.getKey();
                List<ZoomosCityId> cityIdEntries = entry.getValue();

                sendProgress(shopId, operationId, processed, total,
                        "Проверяем " + siteName + " (ITEM, shop=" + shop.getShopName() + ")...");

                // Запрашиваем расширенный диапазон (включает baseline при combineBaseline=true)
                List<ZoomosParsingStats> stats = parseWithRetry(
                        () -> parseItemPage(page, siteName, shop.getShopName(),
                                effectiveDateFrom, dateTo, cityIdEntries, savedRun),
                        siteName, run);
                if (stats == null) {
                    processed += cityIdEntries.size();
                    sendProgress(shopId, operationId, processed, total, "TIMEOUT: " + siteName + " — пропущен");
                    continue;
                }
                List<ZoomosParsingStats> mainStats = splitBaseline(stats, dateFrom, combineBaseline, allBaselineStats);
                if (hasTimeFilter) {
                    mainStats = filterByTime(mainStats, rangeStart, rangeEnd);
                }
                allStats.addAll(mainStats);

                processed += cityIdEntries.size();
                sendProgress(shopId, operationId, processed, total, siteName + " — " + mainStats.size() + " записей");
            }

            // Дополнительный запрос in-progress для сайтов с неполными данными
            // (нет данных совсем, или нет данных по некоторым городам/адресам)
            Set<String> foundCityKeysCheck = new HashSet<>();    // "siteName|cityId"
            Set<String> foundAddressKeysCheck = new HashSet<>(); // "siteName|addressId"
            for (ZoomosParsingStats s : allStats) {
                String cId = extractCityId(s.getCityName());
                if (cId != null) foundCityKeysCheck.add(s.getSiteName() + "|" + cId);
                if (s.getAddressId() != null && !s.getAddressId().isBlank()) {
                    foundAddressKeysCheck.add(s.getSiteName() + "|" + s.getAddressId());
                }
            }

            List<ZoomosCityId> needsInProgress = allCityIds.stream().filter(cid -> {
                String site = cid.getSiteName();
                Set<String> expectedCities = parseCommaSeparated(cid.getCityIds());
                Map<String, Set<String>> addrMapping = parseAddressMapping(cid.getAddressIds());
                Set<String> allAddrs = flattenAddressIds(addrMapping);
                // Сайт без настроенных городов/адресов (глобальная API-выкачка):
                // нужен in-progress запрос если нет завершённых данных за этот период
                if (expectedCities.isEmpty() && allAddrs.isEmpty()) {
                    return allStats.stream().noneMatch(s -> site.equals(s.getSiteName()));
                }
                // Если хотя бы один адрес или один город без покрытия адресами не найден — нужен in-progress
                for (String aid : allAddrs) {
                    if (!foundAddressKeysCheck.contains(site + "|" + aid)) return true;
                }
                Set<String> addrCoveredCities = addrMapping.entrySet().stream()
                        .filter(e -> !e.getKey().isEmpty() && !e.getValue().isEmpty())
                        .map(Map.Entry::getKey).collect(Collectors.toSet());
                for (String cityId : expectedCities) {
                    if (!addrCoveredCities.contains(cityId) && !foundCityKeysCheck.contains(site + "|" + cityId)) {
                        return true;
                    }
                }
                return false;
            }).collect(Collectors.toList());

            if (!needsInProgress.isEmpty()) {
                sendProgress(shopId, operationId, processed, total, "Проверка незавершённых выкачек (" + needsInProgress.size() + ")...");
                // Дедуплицируем по siteName — один запрос на сайт
                Map<String, ZoomosCityId> inProgressBySite = new LinkedHashMap<>();
                for (ZoomosCityId cid : needsInProgress) {
                    inProgressBySite.putIfAbsent(cid.getSiteName(), cid);
                }
                for (ZoomosCityId cid : inProgressBySite.values()) {
                    try {
                        List<ZoomosParsingStats> inProgressStats = parseInProgressPage(
                                page, cid, shop.getShopName(), dateFrom, dateTo, run);
                        // Time-фильтр НЕ применяем к in-progress записям:
                        // overnight-парсинги (старт до timeFrom) нужно сохранить для
                        // отображения "Сейчас идёт" в NOT_FOUND issue.
                        // Промоушен в evaluated-stats контролируется в контроллере по rangeStart.
                        allStats.addAll(inProgressStats);
                    } catch (Exception ex) {
                        log.warn("Ошибка проверки in-progress для {}: {}", cid.getSiteName(), ex.getMessage());
                    }
                }
            }

            if (combineBaseline) {
                log.info("Baseline совмещён с основным парсингом: {} baseline-записей для {}",
                        allBaselineStats.size(), shop.getShopName());
            }

            page.close();
            browser.close();

        } catch (Exception e) {
            log.error("Ошибка проверки выкачки для {}: {}", shop.getShopName(), e.getMessage(), e);
            run.setStatus(CheckRunStatus.FAILED);
            run.setCompletedAt(ZonedDateTime.now());
            // REQUIRES_NEW — коммит FAILED не откатится при re-throw исключения
            self.saveRunImmediate(run);
            sendProgress(shopId, operationId, 0, 0, "Ошибка: " + e.getMessage());
            throw new RuntimeException("Ошибка проверки: " + e.getMessage(), e);
        }

        // Сохраняем основные результаты
        if (!allStats.isEmpty()) {
            parsingStatsRepository.saveAll(allStats);
            // upsertCityNames/Addresses вызываются внутри parseTable ДО фильтрации,
            // поэтому здесь они не нужны — все города и адреса уже сохранены.
        }

        // Сохраняем baseline-записи (города/адреса уже сохранены внутри parseTable)
        if (!allBaselineStats.isEmpty()) {
            parsingStatsRepository.saveAll(allBaselineStats);
        }

        // Подсчитываем итоги (только по основным записям, не baseline)
        updateRunSummary(run, allStats, allCityIds);
        run.setStatus(CheckRunStatus.COMPLETED);
        run.setCompletedAt(ZonedDateTime.now());
        // REQUIRES_NEW: коммит COMPLETED до WebSocket — иначе JS читает БД раньше коммита и видит RUNNING
        run = self.saveRunImmediate(run);

        // REQUIRES_NEW: коммит lastRunAt до WebSocket — JS сразу видит актуальное время
        if (params.getScheduleId() != null) {
            self.saveScheduleLastRunAt(params.getScheduleId());
        }

        sendProgress(shopId, operationId, run.getTotalSites(), run.getTotalSites(), "Проверка завершена");
        return run;
    }

    private void navigateWithSessionRetry(Page page, BrowserContext context, String testUrl, String shopName) {
        int maxAttempts = config.getRetryAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                page.navigate(testUrl);
                page.waitForLoadState(LoadState.NETWORKIDLE);
                if (page.url().contains("/login")) {
                    login(context);
                    page.navigate(testUrl);
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                }
                saveSession(context);
                return;
            } catch (Exception e) {
                if (!playwrightHelper.isTimeoutException(e)) throw e;
                if (attempt == maxAttempts) {
                    throw new RuntimeException("Таймаут при проверке сессии для " + shopName
                            + " после " + maxAttempts + " попыток", e);
                }
                log.warn("Timeout при проверке сессии для {} (попытка {}/{}), повтор через {}с...",
                        shopName, attempt, maxAttempts, config.getRetryDelaySeconds());
                try {
                    Thread.sleep(config.getRetryDelaySeconds() * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
    }

    /**
     * Запускает action с retry при timeout-исключениях.
     * Возвращает null если все попытки исчерпаны — в этом случае таймаут записывается в run.
     */
    private List<ZoomosParsingStats> parseWithRetry(
            java.util.function.Supplier<List<ZoomosParsingStats>> action,
            String siteName, ZoomosCheckRun run) {
        int maxAttempts = config.getRetryAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                boolean isTimeout = playwrightHelper.isTimeoutException(e);
                if (!isTimeout) throw e;
                if (attempt == maxAttempts) {
                    run.setTimeoutCount((run.getTimeoutCount() != null ? run.getTimeoutCount() : 0) + 1);
                    String prev = run.getErrorMessage() != null ? run.getErrorMessage() + "; " : "";
                    run.setErrorMessage(prev + "TIMEOUT: " + siteName + " (попытки исчерпаны)");
                    log.warn("TIMEOUT для {} — все {} попытки исчерпаны, пропускаем сайт", siteName, maxAttempts);
                    return null;
                }
                log.warn("Timeout для {} (попытка {}/{}), повтор через {}с...",
                        siteName, attempt, maxAttempts, config.getRetryDelaySeconds());
                try {
                    Thread.sleep(config.getRetryDelaySeconds() * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
        return null; // unreachable
    }

    /**
     * Разделяет список записей на основные и baseline.
     * Записи с startTime до dateFrom (эксклюзивно) помечаются is_baseline=true
     * и добавляются в baselineAccumulator. Возвращает основные записи.
     *
     * @param combineBaseline если {@code false} — разделение не выполняется, возвращается
     *   оригинальный список (все записи считаются основными, baselineAccumulator не заполняется).
     *   Используется при baselineDays=0, когда baseline не нужен.
     */
    private List<ZoomosParsingStats> splitBaseline(List<ZoomosParsingStats> stats,
                                                    LocalDate mainDateFrom,
                                                    boolean combineBaseline,
                                                    List<ZoomosParsingStats> baselineAccumulator) {
        if (!combineBaseline) {
            return stats;
        }
        List<ZoomosParsingStats> main = new ArrayList<>();
        for (ZoomosParsingStats s : stats) {
            LocalDate sDate = s.getStartTime() != null ? s.getStartTime().toLocalDate() : null;
            if (sDate != null && sDate.isBefore(mainDateFrom)) {
                s.setIsBaseline(true);
                baselineAccumulator.add(s);
            } else {
                main.add(s);
            }
        }
        return main;
    }

    // =========================================================================
    // Вспомогательные типы и методы для фильтрации по адресам
    // =========================================================================

    private record AddressFilterContext(
            Set<String> allowedCityIds,
            Set<String> allowedAddressIds,
            Map<String, Set<String>> allowedAddressesByCityId,
            Map<String, ZoomosCityId> cityIdMap) {}

    /** Строит контекст фильтрации адресов из списка ZoomosCityId.
     *  master_city_id берётся из zoomos_sites (site-level override) → fallback на zoomos_city_ids (legacy). */
    private AddressFilterContext buildAddressFilterContext(List<ZoomosCityId> entries) {
        // Site-level masterCityId override (Task 2: перенос из city_ids → sites)
        Map<String, String> siteMasterOverride = knownSiteRepository.findAllByMasterCityIdNotNull()
                .stream().collect(Collectors.toMap(ZoomosKnownSite::getSiteName, ZoomosKnownSite::getMasterCityId));

        Set<String> allowedCityIds = new HashSet<>();
        Set<String> allowedAddressIds = new HashSet<>();
        Map<String, Set<String>> allowedAddressesByCityId = new HashMap<>();
        Map<String, ZoomosCityId> cityIdMap = new HashMap<>();
        for (ZoomosCityId entry : entries) {
            // Site-level master override; если нет — legacy per-client значение
            String effectiveMasterCityId = siteMasterOverride.getOrDefault(entry.getSiteName(), entry.getMasterCityId());
            String effectiveCityIds = (effectiveMasterCityId != null && !effectiveMasterCityId.isBlank())
                    ? effectiveMasterCityId   // только мастер-город
                    : entry.getCityIds();     // все города как прежде
            if (effectiveCityIds != null && !effectiveCityIds.isBlank()) {
                for (String cid : effectiveCityIds.split(",")) {
                    String trimmed = cid.trim();
                    if (!trimmed.isEmpty()) {
                        allowedCityIds.add(trimmed);
                        cityIdMap.put(trimmed, entry);
                    }
                }
            }
            // address_ids обрабатываем только если effective master_city_id не задан
            if (effectiveMasterCityId == null || effectiveMasterCityId.isBlank()) {
                parseAddressMapping(entry.getAddressIds()).forEach((cityId, addrIds) -> {
                    if (cityId != null && !cityId.isBlank()) {
                        allowedAddressesByCityId.computeIfAbsent(cityId.trim(), k -> new HashSet<>()).addAll(addrIds);
                    } else {
                        allowedAddressIds.addAll(addrIds);
                    }
                });
            }
        }
        return new AddressFilterContext(allowedCityIds, allowedAddressIds, allowedAddressesByCityId, cityIdMap);
    }

    // =========================================================================
    // Парсинг страницы полной выкачки (API)
    // =========================================================================

    private List<ZoomosParsingStats> parseApiPage(Page page, String siteName,
                                                   LocalDate dateFrom, LocalDate dateTo,
                                                   List<ZoomosCityId> cityIdEntries,
                                                   ZoomosCheckRun run) {
        String url = config.getBaseUrl() + "/shops-parser/" + siteName + "/parsing-history"
                + "?upd=" + System.currentTimeMillis()
                + "&dateFrom=" + dateFrom.format(DATE_PARAM_FORMAT)
                + "&dateTo=" + dateTo.format(DATE_PARAM_FORMAT)
                + "&launchDate=&shop=-&site=&cityId=&address=&accountId=&server="
                + "&onlyFinished=1";

        log.info("Парсинг API страницы: {}", url);
        page.navigate(url);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Собираем все допустимые city ID и address ID для фильтрации
        AddressFilterContext ctx = buildAddressFilterContext(cityIdEntries);

        // Для API-сайтов берём только глобальные выкачки (поле "Клиент" пустое)
        return parseTable(page, run, siteName, "API", ctx.allowedCityIds(), ctx.cityIdMap(), ctx.allowedAddressIds(), ctx.allowedAddressesByCityId()).stream()
                .filter(s -> s.getClientName() == null || s.getClientName().isBlank())
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Парсинг страницы ITEM (та же parsing-history + &shop=shopName)
    // =========================================================================

    private List<ZoomosParsingStats> parseItemPage(Page page, String siteName, String shopName,
                                                    LocalDate dateFrom, LocalDate dateTo,
                                                    List<ZoomosCityId> cityIdEntries,
                                                    ZoomosCheckRun run) {
        String url = config.getBaseUrl() + "/shops-parser/" + siteName + "/parsing-history"
                + "?upd=" + System.currentTimeMillis()
                + "&dateFrom=" + dateFrom.format(DATE_PARAM_FORMAT)
                + "&dateTo=" + dateTo.format(DATE_PARAM_FORMAT)
                + "&launchDate=&shop=" + shopName + "&site=&cityId=&address=&accountId=&server="
                + "&onlyFinished=1";

        log.info("Парсинг ITEM страницы: {}", url);
        page.navigate(url);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        AddressFilterContext ctx = buildAddressFilterContext(cityIdEntries);

        return parseTable(page, run, siteName, "ITEM", ctx.allowedCityIds(), ctx.cityIdMap(), ctx.allowedAddressIds(), ctx.allowedAddressesByCityId());
    }

    // =========================================================================
    // Парсинг in-progress страницы для NOT_FOUND сайтов (без &onlyFinished=1)
    // =========================================================================

    private List<ZoomosParsingStats> parseInProgressPage(Page page, ZoomosCityId cid,
                                                          String shopName,
                                                          LocalDate dateFrom, LocalDate dateTo,
                                                          ZoomosCheckRun run) {
        String siteName = cid.getSiteName();
        String checkType = cid.getCheckType() != null ? cid.getCheckType() : "API";

        String shopParam = "ITEM".equals(checkType) ? shopName : "-";
        // Смотрим на 1 день назад от dateFrom: overnight-парсинги стартуют накануне,
        // а после завершения переходят на страницу своей даты старта (не текущего дня)
        String url = config.getBaseUrl() + "/shops-parser/" + siteName + "/parsing-history"
                + "?upd=" + System.currentTimeMillis()
                + "&dateFrom=" + dateFrom.minusDays(1).format(DATE_PARAM_FORMAT)
                + "&dateTo=" + dateTo.format(DATE_PARAM_FORMAT)
                + "&launchDate=&shop=" + shopParam + "&site=&cityId=&address=&accountId=&server=";

        log.info("Парсинг in-progress страницы: {}", url);
        page.navigate(url);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        AddressFilterContext ctx = buildAddressFilterContext(List.of(cid));

        List<ZoomosParsingStats> stats = parseTable(page, run, siteName, checkType, ctx.allowedCityIds(), ctx.cityIdMap(), ctx.allowedAddressIds(), ctx.allowedAddressesByCityId());
        // Для API — только глобальные выкачки (без клиента)
        if ("API".equals(checkType)) {
            stats = stats.stream()
                    .filter(s -> s.getClientName() == null || s.getClientName().isBlank())
                    .collect(Collectors.toList());
        }
        // Помечаем как незавершённые
        stats.forEach(s -> s.setIsFinished(false));
        return stats;
    }

    // =========================================================================
    // Общий парсинг HTML-таблицы
    // =========================================================================

    @SuppressWarnings("unchecked")
    private List<ZoomosParsingStats> parseTable(Page page, ZoomosCheckRun run,
                                                 String defaultSiteName, String checkType,
                                                 Set<String> allowedCityIds,
                                                 Map<String, ZoomosCityId> cityIdMap,
                                                 Set<String> allowedAddressIds,
                                                 Map<String, Set<String>> allowedAddressesByCityId) {
        List<ZoomosParsingStats> results = new ArrayList<>();

        // Извлекаем все данные таблицы одним вызовом JS в браузере,
        // чтобы избежать "object has been collected" при большом количестве строк
        Object evalResult = page.evaluate("() => {\n" +
                "  const table = document.querySelector('table#parser-history-table') || document.querySelector('table');\n" +
                "  if (!table) return null;\n" +
                "  const ths = table.querySelectorAll('thead th');\n" +
                "  const headers = Array.from(ths.length ? ths : table.querySelectorAll('th'))\n" +
                "    .map(th => th.innerText.trim().replace(/\\s+/g, ' ').toLowerCase());\n" +
                "  const rows = [];\n" +
                "  table.querySelectorAll('tr').forEach(tr => {\n" +
                "    const tds = tr.querySelectorAll('td');\n" +
                "    if (tds.length >= 5) {\n" +
                "      rows.push(Array.from(tds).map(td => td.innerText.trim()));\n" +
                "    }\n" +
                "  });\n" +
                "  return { headers: headers, rows: rows };\n" +
                "}");

        if (evalResult == null) {
            log.warn("Таблица не найдена на странице: {}", page.url());
            return results;
        }

        Map<String, Object> tableData = (Map<String, Object>) evalResult;
        List<String> headersList = (List<String>) tableData.get("headers");
        List<List<String>> rowsList = (List<List<String>>) tableData.get("rows");

        Map<String, Integer> colIndex = new LinkedHashMap<>();
        for (int i = 0; i < headersList.size(); i++) {
            colIndex.put(headersList.get(i), i);
        }

        // Fallback-маппинг колонок: если точное имя не найдено, ищем по вхождению
        Map<String, String[]> fallbackAliases = Map.of(
                "старт (общий)", new String[]{"старт"},
                "кол-во товаров", new String[]{"товаров", "количество товаров"},
                "кол-во ошибок", new String[]{"ошибок", "количество ошибок"},
                "кол-во категорий", new String[]{"категорий", "количество категорий"},
                "завершено (всего)", new String[]{"завершено"},
                "в наличии", new String[]{"наличии"}
        );
        for (Map.Entry<String, String[]> alias : fallbackAliases.entrySet()) {
            if (!colIndex.containsKey(alias.getKey())) {
                for (String alt : alias.getValue()) {
                    for (Map.Entry<String, Integer> col : colIndex.entrySet()) {
                        if (col.getKey().contains(alt)) {
                            colIndex.put(alias.getKey(), col.getValue());
                            log.info("Fallback маппинг: '{}' → колонка '{}' (idx={})", alias.getKey(), col.getKey(), col.getValue());
                            break;
                        }
                    }
                    if (colIndex.containsKey(alias.getKey())) break;
                }
            }
        }

        log.info("Найдены столбцы ({}): {}", colIndex.size(), colIndex);

        boolean firstRow = true;
        for (List<String> cells : rowsList) {
            try {
                // Debug-логирование первой строки для диагностики маппинга
                if (firstRow) {
                    log.info("Первая строка raw данных ({} ячеек): {}", cells.size(), cells);
                    firstRow = false;
                }

                String id = getCellValue(cells, colIndex, "id");
                String server = getCellValue(cells, colIndex, "сервер");
                String clientName = getCellValue(cells, colIndex, "клиент");
                String site = getCellValue(cells, colIndex, "сайт");
                String city = getCellValue(cells, colIndex, "город");
                String startStr = getCellValue(cells, colIndex, "старт (общий)");
                String finishStr = getCellValue(cells, colIndex, "финиш");
                String countStr = getCellValue(cells, colIndex, "кол-во товаров");
                String categoriesStr = getCellValue(cells, colIndex, "кол-во категорий");
                String inStockStr = getCellValue(cells, colIndex, "в наличии");
                String errorsStr = getCellValue(cells, colIndex, "кол-во ошибок");
                String updatedStr = getCellValue(cells, colIndex, "обновлено");
                String completionStr = getCellValue(cells, colIndex, "завершено (всего)");
                String timeStr = getCellValue(cells, colIndex, "время");
                String parserDesc = getCellValue(cells, colIndex, "парсер");
                String accountRaw = getCellValue(cells, colIndex, "аккаунт");
                String accountName = playwrightHelper.parseAccountName(accountRaw);

                String siteName = (site != null && !site.isEmpty()) ? site : defaultSiteName;
                if (siteName == null) continue;

                String addressRaw = getCellValue(cells, colIndex, "адрес");
                String addressId  = extractAddressId(addressRaw);
                String cityId     = extractCityId(city);

                ZoomosCityId cityIdRef = cityId != null ? cityIdMap.get(cityId) : null;

                ZonedDateTime startTime = parseDateTime(startStr);
                ZonedDateTime finishTime = parseDateTime(finishStr);
                LocalDate parsingDate = startTime != null
                        ? startTime.toLocalDate()
                        : LocalDate.now();

                ZoomosParsingStats stat = ZoomosParsingStats.builder()
                        .checkRun(run)
                        .cityIdRef(cityIdRef)
                        .parsingId(parseLong(id))
                        .siteName(siteName)
                        .cityName(city)
                        .serverName(server)
                        .clientName(clientName)
                        .addressId(addressId)
                        .addressName(addressRaw)
                        .startTime(startTime)
                        .finishTime(finishTime)
                        .updatedTime(parseDateTime(updatedStr))
                        .totalProducts(parseInt(countStr))
                        .categoryCount(parseInt(categoriesStr))
                        .inStock(parseInt(inStockStr))
                        .errorCount(parseInt(errorsStr))
                        .completionTotal(completionStr)
                        .completionPercent(extractPercent(completionStr))
                        .parsingDuration(timeStr)
                        .parsingDurationMinutes(parseDurationMinutes(timeStr))
                        .parserDescription(parserDesc)
                        .accountName(accountName)
                        .parsingDate(parsingDate)
                        .checkType(ZoomosCheckType.valueOf(checkType))
                        .isFinished(true)
                        .build();

                results.add(stat);

            } catch (Exception e) {
                log.warn("Ошибка парсинга строки таблицы: {}", e.getMessage());
            }
        }

        // Сохраняем все уникальные паттерны парсера, города и адреса ДО фильтрации.
        // upsertCityNames/Addresses ОБЯЗАТЕЛЬНО до фильтра: адреса/города, которых нет
        // в allowedCityIds/allowedAddressIds, будут отсеяны фильтром и не попадут в справочник.
        log.info("parseTable [{}] до фильтра: {} записей, примеры городов: {}", defaultSiteName, results.size(),
                results.stream().map(ZoomosParsingStats::getCityName).filter(Objects::nonNull)
                        .distinct().limit(5).collect(Collectors.joining(", ")));
        upsertParserPatterns(defaultSiteName, results);
        upsertCityNames(results);
        upsertCityAddresses(results);

        // Применяем комбинированный фильтр: address-покрытые города → только по addressId (city-specific),
        // остальные города → по cityId
        if (!allowedCityIds.isEmpty() || !allowedAddressIds.isEmpty() || !allowedAddressesByCityId.isEmpty()) {
            // Города с city-specific ограничениями по адресам (из конфига)
            Set<String> addressConstrainedCityIds = allowedAddressesByCityId.entrySet().stream()
                    .filter(e -> !e.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            // Для обратной совместимости: города из данных с плоскими адресами
            Set<String> flatAddressCoveredCityIds = results.stream()
                    .filter(s -> s.getAddressId() != null && allowedAddressIds.contains(s.getAddressId()))
                    .map(s -> extractCityId(s.getCityName()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            results = results.stream().filter(s -> {
                String addrId = s.getAddressId();
                String cId    = extractCityId(s.getCityName());
                // City-specific: если для города есть address-ограничения — только через них
                if (cId != null && addressConstrainedCityIds.contains(cId)) {
                    Set<String> addrsForCity = allowedAddressesByCityId.get(cId);
                    return addrId != null && addrsForCity != null && addrsForCity.contains(addrId);
                }
                // Плоские адреса (без привязки к городу — обратная совместимость)
                if (addrId != null && allowedAddressIds.contains(addrId)) return true;
                // Строка подходит по cityId И этот город не покрыт адресами → включаем
                if (cId != null && allowedCityIds.contains(cId) && !flatAddressCoveredCityIds.contains(cId)) return true;
                return false;
            }).collect(Collectors.toList());
        }

        // Применяем фильтр парсера (по parserDescription через config cityIdMap)
        results = results.stream()
                .filter(s -> matchesParserFilter(s, cityIdMap.get(extractCityId(s.getCityName()))))
                .collect(Collectors.toList());

        log.info("Распарсено {} записей (checkType={}) со страницы: {}", results.size(), checkType, page.url());
        return results;
    }

    /**
     * Проверяет, проходит ли строка статистики фильтр парсера, заданный в конфиге cityId.
     */
    private boolean matchesParserFilter(ZoomosParsingStats s, ZoomosCityId config) {
        if (config == null) return true;
        String pd = s.getParserDescription() != null ? s.getParserDescription().toLowerCase() : "";

        // include: пустой = всё разрешено. Разделитель — точка с запятой (паттерны сами содержат запятые)
        // AND и OR — одинаковая фильтрация (include ANY), различие только в оценке завершённости.
        if (config.getParserInclude() != null && !config.getParserInclude().isBlank()) {
            // Если parserDescription неизвестен — пропускаем строку (не фильтруем)
            if (pd.isEmpty()) return true;
            List<String> parts = Arrays.stream(config.getParserInclude().split(";"))
                    .map(String::trim).filter(p -> !p.isEmpty())
                    .map(String::toLowerCase).collect(Collectors.toList());
            if (!parts.stream().anyMatch(pd::contains)) return false;
        }

        // exclude: всегда OR — если хотя бы одна подстрока совпала, исключаем
        if (config.getParserExclude() != null && !config.getParserExclude().isBlank()) {
            if (!pd.isEmpty()) {
                boolean excluded = Arrays.stream(config.getParserExclude().split(";"))
                        .map(String::trim).filter(p -> !p.isEmpty())
                        .anyMatch(p -> pd.contains(p.toLowerCase()));
                if (excluded) return false;
            }
        }

        return true;
    }

    /**
     * Batch upsert уникальных (siteName, parserDescription) в справочник паттернов.
     */
    private void upsertParserPatterns(String siteName, List<ZoomosParsingStats> stats) {
        stats.stream()
                .map(ZoomosParsingStats::getParserDescription)
                .filter(pd -> pd != null && !pd.isBlank())
                .distinct()
                .forEach(pd -> {
                    try {
                        parserPatternRepository.upsert(siteName, pd);
                    } catch (Exception e) {
                        log.warn("Не удалось сохранить паттерн парсера для {}: {}", siteName, e.getMessage());
                    }
                });
    }

    // =========================================================================
    // Вспомогательные методы парсинга
    // =========================================================================

    private List<ZoomosParsingStats> filterByTime(List<ZoomosParsingStats> stats,
                                                   ZonedDateTime rangeStart,
                                                   ZonedDateTime rangeEnd) {
        return stats.stream()
                .filter(s -> {
                    // Нижняя граница: выкачка должна стартовать не раньше rangeStart
                    if (s.getStartTime() != null && s.getStartTime().isBefore(rangeStart)) {
                        return false;
                    }
                    // Верхняя граница: выкачка должна быть завершена до rangeEnd
                    // Используем finishTime (когда данные реально готовы), при отсутствии — startTime
                    ZonedDateTime endRef = s.getFinishTime() != null ? s.getFinishTime() : s.getStartTime();
                    if (endRef != null && endRef.isAfter(rangeEnd)) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private String getCellValue(List<String> cells, Map<String, Integer> colIndex, String colName) {
        Integer idx = colIndex.get(colName);
        if (idx == null || idx >= cells.size()) return null;
        String text = cells.get(idx);
        return (text == null || text.isEmpty()) ? null : text;
    }

    private String extractAddressId(String addressStr) {
        if (addressStr == null || addressStr.isEmpty()) return null;
        // "[14342] Братск, Ленина пр-кт, 7" → "14342"
        String trimmed = addressStr.trim();
        if (trimmed.startsWith("[")) {
            int close = trimmed.indexOf("]");
            if (close > 1) return trimmed.substring(1, close).trim();
        }
        return null;
    }

    public static String extractCityId(String cityStr) {
        if (cityStr == null || cityStr.isEmpty()) return null;
        // "3612 - Нижний Новгород" → "3612"
        String trimmed = cityStr.trim();
        int dashIdx = trimmed.indexOf(" - ");
        if (dashIdx > 0) {
            return trimmed.substring(0, dashIdx).trim();
        }
        // Если просто число
        if (trimmed.matches("\\d+")) return trimmed;
        return null;
    }

    public static String extractCityName(String cityStr) {
        if (cityStr == null) return null;
        int dashIdx = cityStr.indexOf(" - ");
        if (dashIdx >= 0) return cityStr.substring(dashIdx + 3).trim();
        return null;
    }

    /**
     * Нормализует city-строку для отображения: "1913" → "1913 - Алматы" (если имя есть в map).
     * Если cityStr уже содержит " - " — возвращает как есть.
     * Единый метод для всех страниц и контроллеров.
     */
    public static String resolveCityDisplay(String cityStr, Map<String, String> cityNamesMap) {
        if (cityStr == null || cityStr.isBlank()) return cityStr;
        if (cityStr.contains(" - ")) return cityStr;
        String name = cityNamesMap != null ? cityNamesMap.get(cityStr) : null;
        return name != null ? cityStr + " - " + name : cityStr;
    }

    private void upsertCityAddresses(List<ZoomosParsingStats> stats) {
        int saved = 0, skipped = 0;
        for (ZoomosParsingStats s : stats) {
            String cityId = extractCityId(s.getCityName());
            if (cityId != null && s.getAddressId() != null && !s.getAddressId().isBlank()) {
                try {
                    cityAddressRepository.upsert(cityId, s.getAddressId(), s.getAddressName());
                    saved++;
                } catch (Exception e) {
                    log.warn("upsertCityAddresses: cityId={} addressId={}: {}", cityId, s.getAddressId(), e.getMessage());
                }
            } else {
                skipped++;
            }
        }
        log.info("upsertCityAddresses: сохранено={}, пропущено={} (нет cityId или addressId)", saved, skipped);
    }

    private void upsertCityNames(List<ZoomosParsingStats> stats) {
        Map<String, String> names = new LinkedHashMap<>();
        for (ZoomosParsingStats s : stats) {
            String id   = extractCityId(s.getCityName());
            String name = extractCityName(s.getCityName());
            if (id != null && name != null && !name.isBlank() && !names.containsKey(id)) {
                names.put(id, name);
            }
        }
        log.info("upsertCityNames: уникальных городов для сохранения={} из {} записей", names.size(), stats.size());
        for (Map.Entry<String, String> e : names.entrySet()) {
            try {
                cityNameRepository.upsert(e.getKey(), e.getValue());
            } catch (Exception ex) {
                log.warn("upsertCityNames: cityId={}: {}", e.getKey(), ex.getMessage());
            }
        }
    }

    public static Set<String> parseCommaSeparated(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptySet();
        Set<String> result = new HashSet<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    /**
     * Парсит addressIds в структурированный маппинг cityId → Set<addressId>.
     * JSON формат: {"3435":["18121","18122"],"4036":[]}
     * Плоский формат (обратная совместимость): "18121,18122" → {"":["18121","18122"]}
     */
    public static Map<String, Set<String>> parseAddressMapping(String addressIds) {
        if (addressIds == null || addressIds.isBlank()) return Collections.emptyMap();
        String trimmed = addressIds.trim();
        if (trimmed.startsWith("{")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> raw = STATIC_MAPPER.readValue(trimmed, Map.class);
                Map<String, Set<String>> result = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : raw.entrySet()) {
                    Set<String> addrs = new HashSet<>();
                    if (entry.getValue() instanceof java.util.List<?> list) {
                        for (Object item : list) {
                            // Разбиваем по запятой на случай, если несколько ID были
                            // сохранены одной строкой (напр. "394,101,456,34")
                            for (String part : String.valueOf(item).split(",")) {
                                String s = part.trim();
                                if (!s.isEmpty()) addrs.add(s);
                            }
                        }
                    }
                    result.put(entry.getKey(), addrs);
                }
                return result;
            } catch (Exception e) {
                // Fallback: парсим как плоский
            }
        }
        // Плоский формат — без привязки к городу
        Set<String> flat = parseCommaSeparated(trimmed);
        if (flat.isEmpty()) return Collections.emptyMap();
        return Map.of("", flat);
    }

    /**
     * Извлекает все addressId из маппинга (все города).
     */
    public static Set<String> flattenAddressIds(Map<String, Set<String>> mapping) {
        return mapping.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
    }

    private ZonedDateTime parseDateTime(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            // Формат: "15.02.26 21:40" или "15.02.26 06:28 (14.02.26 19:01)"
            // Берём первую часть до скобки
            String clean = str.contains("(") ? str.substring(0, str.indexOf("(")).trim() : str.trim();
            var local = java.time.LocalDateTime.parse(clean, DATETIME_PARSE_FORMAT);
            return local.atZone(java.time.ZoneOffset.UTC);
        } catch (Exception e) {
            log.trace("Не удалось распарсить дату '{}': {}", str, e.getMessage());
            return null;
        }
    }

    private Integer parseInt(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            return Integer.parseInt(str.replaceAll("[^\\d]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            return Long.parseLong(str.replaceAll("[^\\d]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer extractPercent(String str) {
        if (str == null || str.isEmpty()) return null;
        // "100% (58%)" → 100, "100% (100%)" → 100
        try {
            String numStr = str.split("%")[0].trim();
            return Integer.parseInt(numStr);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseDurationMinutes(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            // "27 мин" → 27, "1 ч 23 мин" → 83, "112 мин" → 112
            str = str.toLowerCase().trim();
            int minutes = 0;
            if (str.contains("ч")) {
                String hoursPart = str.substring(0, str.indexOf("ч")).trim();
                // Может быть "1 ч 23 мин" — берём последнее число перед "ч"
                String[] parts = hoursPart.split("\\s+");
                minutes += Integer.parseInt(parts[parts.length - 1]) * 60;
                str = str.substring(str.indexOf("ч") + 1).trim();
            }
            String nums = str.replaceAll("[^\\d]", "");
            if (!nums.isEmpty()) {
                minutes += Integer.parseInt(nums);
            }
            return minutes > 0 ? minutes : null;
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // Baseline-анализ: загрузка исторических данных и вычисление медианы
    // =========================================================================

    /**
     * Проверяет наличие достаточного количества исторических данных в БД.
     * Если данных < 3, парсит последний день baseline-периода через Playwright.
     * Записи сохраняются с is_baseline=true и привязываются к текущему run.
     */
    private void fetchBaselineIfNeeded(Page page, ZoomosCheckRun run,
                                        List<ZoomosCityId> allCityIds,
                                        LocalDate dateFrom, String shopName,
                                        String operationId) {
        LocalDate baselineFrom = dateFrom.minusDays(run.getBaselineDays());
        LocalDate baselineTo   = dateFrom.minusDays(1);
        ZonedDateTime bFrom = baselineFrom.atStartOfDay(java.time.ZoneOffset.UTC);
        ZonedDateTime bTo   = baselineTo.atTime(23, 59).atZone(java.time.ZoneOffset.UTC);

        // Группируем по siteName+checkType
        Map<String, List<ZoomosCityId>> byTypeAndSite = allCityIds.stream()
                .collect(Collectors.groupingBy(c ->
                        c.getSiteName() + "|" + (c.getCheckType() != null ? c.getCheckType() : "API")));

        List<ZoomosParsingStats> allBaselineToSave = new ArrayList<>();

        for (Map.Entry<String, List<ZoomosCityId>> entry : byTypeAndSite.entrySet()) {
            String site = entry.getValue().get(0).getSiteName();
            String checkType = entry.getValue().get(0).getCheckType() != null
                    ? entry.getValue().get(0).getCheckType() : "API";

            // Проверяем сколько исторических записей есть в БД
            List<ZoomosParsingStats> existing = parsingStatsRepository.findForBaseline(
                    site, null, null, bFrom, bTo);

            if (existing.size() >= 3) {
                log.debug("Baseline для {}: {} записей в БД — парсинг не нужен", site, existing.size());
                continue;
            }

            log.info("Baseline для {}: только {} записей в БД — парсим последний день baseline ({})",
                    site, existing.size(), baselineTo);
            sendProgress(run.getShop().getId(), operationId, 0, 0, "Загрузка baseline " + site + "...");

            try {
                List<ZoomosCityId> cityIdEntries = entry.getValue();
                List<ZoomosParsingStats> fetched;
                if ("API".equals(checkType)) {
                    fetched = parseApiPage(page, site, baselineTo, baselineTo, cityIdEntries, run);
                } else {
                    fetched = parseItemPage(page, site, shopName, baselineTo, baselineTo, cityIdEntries, run);
                }
                // Помечаем как baseline
                fetched.forEach(s -> s.setIsBaseline(true));
                allBaselineToSave.addAll(fetched);
                log.info("Baseline для {}: загружено {} записей", site, fetched.size());
            } catch (Exception ex) {
                log.warn("Ошибка парсинга baseline для {}: {}", site, ex.getMessage());
            }
        }

        if (!allBaselineToSave.isEmpty()) {
            parsingStatsRepository.saveAll(allBaselineToSave);
            log.info("Baseline: сохранено {} записей", allBaselineToSave.size());
        }
    }


    // =========================================================================
    // Итоги проверки
    // =========================================================================

    private void updateRunSummary(ZoomosCheckRun run, List<ZoomosParsingStats> stats,
                                   List<ZoomosCityId> allCityIds) {
        try {
            List<com.java.dto.zoomos.ZoomosSiteResult> results =
                    analysisService.analyze(run.getId(), null, null, 60);
            int ok = 0, warn = 0, err = 0, notFound = 0;
            for (com.java.dto.zoomos.ZoomosSiteResult r : results) {
                switch (r.getStatus()) {
                    case OK         -> ok++;
                    case WARNING, TREND -> warn++;
                    case CRITICAL   -> {
                        err++;
                        boolean isNotFound = r.getStatusReasons().stream()
                                .anyMatch(i -> i.reason() == com.java.dto.zoomos.StatusReason.NOT_FOUND);
                        if (isNotFound) notFound++;
                    }
                    default -> ok++;
                }
            }
            run.setOkCount(ok);
            run.setWarningCount(warn);
            run.setErrorCount(err);
            run.setNotFoundCount(notFound);
        } catch (Exception e) {
            log.warn("Не удалось подсчитать счётчики через analyse: {}", e.getMessage());
            run.setOkCount(0); run.setWarningCount(0); run.setErrorCount(0); run.setNotFoundCount(0);
        }
    }

    private void sendProgress(Long shopId, String operationId, int current, int total, String message) {
        Map<String, Object> progress = Map.of(
                "current", current,
                "total", total,
                "message", message,
                "percent", total > 0 ? (current * 100 / total) : 0
        );
        if (operationId != null) {
            messagingTemplate.convertAndSend("/topic/zoomos-check/" + operationId, progress);
        }
        if (shopId != null) {
            messagingTemplate.convertAndSend("/topic/zoomos-check/shop/" + shopId, progress);
        }
    }

    // =========================================================================
    // Авторизация
    // =========================================================================

    private boolean loadSession(BrowserContext context) {
        return sessionRepository.findTopByOrderByUpdatedAtDesc().map(session -> {
            try {
                List<Map<String, Object>> rawCookies =
                        objectMapper.readValue(session.getCookies(),
                                new TypeReference<List<Map<String, Object>>>() {});
                List<Cookie> cookies = new ArrayList<>();
                for (Map<String, Object> raw : rawCookies) {
                    String name = (String) raw.get("name");
                    String value = (String) raw.get("value");
                    if (name == null || value == null) continue;
                    Cookie c = new Cookie(name, value);
                    if (raw.get("domain") != null) c.setDomain((String) raw.get("domain"));
                    if (raw.get("path") != null) c.setPath((String) raw.get("path"));
                    if (raw.get("expires") != null) c.setExpires(((Number) raw.get("expires")).doubleValue());
                    if (raw.get("httpOnly") != null) c.setHttpOnly((Boolean) raw.get("httpOnly"));
                    if (raw.get("secure") != null) c.setSecure((Boolean) raw.get("secure"));
                    if (raw.get("sameSite") != null) {
                        try { c.setSameSite(SameSiteAttribute.valueOf((String) raw.get("sameSite"))); }
                        catch (IllegalArgumentException ignored) {}
                    }
                    cookies.add(c);
                }
                context.addCookies(cookies);
                return true;
            } catch (Exception e) {
                log.warn("Не удалось загрузить куки: {}", e.getMessage());
                return false;
            }
        }).orElse(false);
    }

    private void login(BrowserContext context) {
        Page page = context.newPage();
        try {
            page.navigate(config.getBaseUrl() + "/login");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.fill("input[name='j_username']", config.getUsername());
            page.fill("input[name='j_password']", config.getPassword());
            page.click("input[type='submit']");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            if (page.url().contains("/login")) {
                throw new RuntimeException("Авторизация не удалась");
            }
        } finally {
            page.close();
        }
    }

    private void saveSession(BrowserContext context) {
        try {
            List<Cookie> cookies = context.cookies();
            String cookiesJson = objectMapper.writeValueAsString(cookies);
            sessionRepository.findTopByOrderByUpdatedAtDesc().ifPresentOrElse(
                    session -> {
                        session.setCookies(cookiesJson);
                        sessionRepository.save(session);
                    },
                    () -> sessionRepository.save(ZoomosSession.builder().cookies(cookiesJson).build())
            );
        } catch (Exception e) {
            log.warn("Не удалось сохранить куки: {}", e.getMessage());
        }
    }


}
