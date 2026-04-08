package com.java.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.config.ZoomosConfig;
import com.java.dto.zoomos.GroupEvalResult;
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

            // Проверяем сессию
            String testUrl = config.getBaseUrl() + "/shop/" + shop.getShopName() + "/settings";
            page.navigate(testUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            if (page.url().contains("/login")) {
                login(context);
                page.navigate(testUrl);
                page.waitForLoadState(LoadState.NETWORKIDLE);
            }
            saveSession(context);

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

    /**
     * Вычисляет медианные значения метрик по списку исторических выкачек.
     * Метрики нормализованы по числу товаров — устойчивы к росту ассортимента:
     *   "stockRatio"    — inStock / totalProducts (доля наличия)
     *   "errorRate"     — errorCount / totalProducts × 100 (% ошибок от товаров)
     *   "durationRate"  — durationMinutes / totalProducts × 1000 (мин на 1000 товаров)
     */
    public Map<String, Double> computeMedianBaseline(List<ZoomosParsingStats> historicalStats) {
        Map<String, Double> result = new HashMap<>();

        List<Double> stockRatios = historicalStats.stream()
                .filter(s -> s.getTotalProducts() != null && s.getTotalProducts() > 0 && s.getInStock() != null)
                .map(s -> (double) s.getInStock() / s.getTotalProducts())
                .sorted().collect(Collectors.toList());
        if (!stockRatios.isEmpty()) result.put("stockRatio", computeMedian(stockRatios));

        // errorRate = % ошибок от числа товаров (масштабируется с размером сайта)
        List<Double> errorRates = historicalStats.stream()
                .filter(s -> s.getErrorCount() != null && s.getTotalProducts() != null && s.getTotalProducts() > 0)
                .map(s -> s.getErrorCount() * 100.0 / s.getTotalProducts())
                .sorted().collect(Collectors.toList());
        if (!errorRates.isEmpty()) result.put("errorRate", computeMedian(errorRates));

        // durationRate = мин/1000 товаров (рост ассортимента = допустимо дольше)
        List<Double> durationRates = historicalStats.stream()
                .filter(s -> s.getParsingDurationMinutes() != null && s.getParsingDurationMinutes() > 0
                          && s.getTotalProducts() != null && s.getTotalProducts() > 0)
                .map(s -> s.getParsingDurationMinutes() * 1000.0 / s.getTotalProducts())
                .sorted().collect(Collectors.toList());
        if (!durationRates.isEmpty()) result.put("durationRate", computeMedian(durationRates));

        return result;
    }

    private double computeMedian(List<Double> sorted) {
        int n = sorted.size();
        if (n == 0) return -1;
        int mid = n / 2;
        return n % 2 == 0 ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0 : sorted.get(mid);
    }

    /**
     * Сравнивает последнюю выкачку с baseline-медианой.
     * Возвращает список TREND_WARNING сообщений (пустой если всё в норме).
     */
    public List<String> evaluateTrend(ZoomosParsingStats current,
                                       Map<String, Double> baseline,
                                       int dropThreshold, int errorGrowthThreshold,
                                       LocalDate baselineFrom, LocalDate baselineTo) {
        List<String> warnings = new ArrayList<>();
        String period = baselineFrom.format(DD_MM) + "–" + baselineTo.format(DD_MM);
        double dropFrac  = dropThreshold / 100.0;
        double errFrac   = errorGrowthThreshold / 100.0;

        // Stock ratio
        if (baseline.containsKey("stockRatio")
                && current.getTotalProducts() != null && current.getTotalProducts() > 0
                && current.getInStock() != null) {
            double cur  = (double) current.getInStock() / current.getTotalProducts();
            double base = baseline.get("stockRatio");
            if (base > 0 && (base - cur) / base > dropFrac) {
                warnings.add(String.format(
                        "Доля «В наличии» снизилась: было %.1f%% → стало %.1f%% (−%.0f%%) [норма %s]",
                        base * 100, cur * 100, (base - cur) / base * 100, period));
            }
        }

        // Error rate (% от числа товаров — масштабируется с размером сайта)
        if (baseline.containsKey("errorRate") && current.getErrorCount() != null
                && current.getTotalProducts() != null && current.getTotalProducts() > 0) {
            double curRate  = current.getErrorCount() * 100.0 / current.getTotalProducts();
            double baseRate = baseline.get("errorRate");
            double deltaRate = curRate - baseRate;
            if (baseRate > 0 && deltaRate / baseRate > errFrac && deltaRate > 0.5) {
                // Предупреждаем только если рост > порога И абсолютная дельта rate > 0.5%
                warnings.add(String.format(
                        "Рост ошибок: было %.2f%% → стало %.2f%% от товаров (+%.0f%%) [норма %s]",
                        baseRate, curRate, deltaRate / baseRate * 100, period));
            } else if (baseRate == 0) {
                // Появились ошибки там где их раньше не было.
                // Порог масштабируется с errFrac: при trendErrorThreshold=30% → >3%, при 100% → >10%
                double appearedThreshold = Math.max(1.0, errFrac * 10);
                if (curRate > appearedThreshold) {
                    warnings.add(String.format(
                            "Появились ошибки: %.2f%% товаров [норма %s]", curRate, period));
                }
            }
        }

        // Duration rate (мин/1000 товаров — рост ассортимента учитывается автоматически)
        // Уменьшение скорости выкачки — всегда OK, предупреждаем только при замедлении
        if (baseline.containsKey("durationRate") && current.getParsingDurationMinutes() != null
                && current.getTotalProducts() != null && current.getTotalProducts() > 0) {
            double curRate  = current.getParsingDurationMinutes() * 1000.0 / current.getTotalProducts();
            double baseRate = baseline.get("durationRate");
            if (baseRate > 0 && curRate > baseRate) {
                double pct = (curRate - baseRate) / baseRate;
                // Предупреждаем если замедление > порога И дельта rate > 0.1 мин/1000 тов.
                if (pct > dropFrac && (curRate - baseRate) > 0.1) {
                    warnings.add(String.format(
                            "Скорость выкачки снизилась: было %.2f → стало %.2f мин/1000 тов. (+%.0f%%) [норма %s]",
                            baseRate, curRate, pct * 100, period));
                }
            }
        }

        return warnings;
    }

    // =========================================================================
    // Итоги проверки
    // =========================================================================

    private void updateRunSummary(ZoomosCheckRun run, List<ZoomosParsingStats> stats,
                                   List<ZoomosCityId> allCityIds) {
        // Разделяем stats на "готовые для группировки" и "реально in-progress".
        // "Готовые" = isFinished != false (завершённые из finished-страницы)
        //           ИЛИ isFinished=false но completionPercent >= 100 (100%-ные in-progress, как в контроллере).
        // Это важно когда finished-страница (onlyFinished=1) вернула 0 записей, а все данные —
        // в in-progress (как для ITEM-сайтов, где onlyFinished не показывает текущее состояние).
        List<ZoomosParsingStats> finishedStats = stats.stream()
                .filter(s -> !Boolean.FALSE.equals(s.getIsFinished())
                        || (s.getCompletionPercent() != null && s.getCompletionPercent() >= 100))
                .collect(Collectors.toList());
        // Только реально незавершённые (<100%) — для расширения групп при checkParserCompleteness
        Map<String, List<ZoomosParsingStats>> inProgressBySite = stats.stream()
                .filter(s -> Boolean.FALSE.equals(s.getIsFinished())
                        && (s.getCompletionPercent() == null || s.getCompletionPercent() < 100))
                .collect(Collectors.groupingBy(s -> s.getSiteName() != null ? s.getSiteName() : ""));

        // Группируем данные по site+city+address (уникальная группа = одна "линия выкачки")
        Map<String, List<ZoomosParsingStats>> grouped = finishedStats.stream()
                .collect(Collectors.groupingBy(s -> {
                    String key = s.getSiteName() + "|" + (s.getCityName() != null ? s.getCityName() : "");
                    if (s.getAddressId() != null && !s.getAddressId().isBlank()) {
                        key += "|" + s.getAddressId();
                    }
                    return key;
                }));
        int notFound = 0;
        int ok = 0;
        int warning = 0;
        int error = 0;

        // Строим множества найденных городов и адресов из реальных данных
        Set<String> foundCityKeys = new HashSet<>();    // "siteName|cityId"
        Set<String> foundAddressKeys = new HashSet<>(); // "siteName|addressId"
        Map<String, String> addressToCityFromData = new HashMap<>(); // "siteName|addressId" → cityId

        for (ZoomosParsingStats s : stats) {
            String cId = extractCityId(s.getCityName());
            if (cId != null) foundCityKeys.add(s.getSiteName() + "|" + cId);
            if (s.getAddressId() != null && !s.getAddressId().isBlank()) {
                foundAddressKeys.add(s.getSiteName() + "|" + s.getAddressId());
                if (cId != null) addressToCityFromData.put(s.getSiteName() + "|" + s.getAddressId(), cId);
            }
        }

        // Проверяем каждый ожидаемый город/адрес
        for (ZoomosCityId cid : allCityIds) {
            String site = cid.getSiteName();
            Set<String> expectedCities = parseCommaSeparated(cid.getCityIds());
            Map<String, Set<String>> addrMapping = parseAddressMapping(cid.getAddressIds());

            // Города покрытые адресами — из конфигурации (не из данных парсинга)
            Set<String> addressCoveredCities = addrMapping.entrySet().stream()
                    .filter(e -> !e.getKey().isEmpty() && !e.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            // Проверяем адреса
            Set<String> allExpectedAddresses = flattenAddressIds(addrMapping);
            for (String aid : allExpectedAddresses) {
                if (!foundAddressKeys.contains(site + "|" + aid)) {
                    notFound++;
                }
            }

            // Проверяем города без покрытия адресами
            for (String cityId : expectedCities) {
                if (addressCoveredCities.contains(cityId)) continue;
                if (!foundCityKeys.contains(site + "|" + cityId)) {
                    notFound++;
                }
            }
        }

        // Загружаем сайты с ignoreStock один раз перед циклом оценки
        Set<String> ignoreStockSites = knownSiteRepository.findAllByIgnoreStockTrue()
                .stream().map(ZoomosKnownSite::getSiteName).collect(Collectors.toSet());
        int minAbsErrors = run.getMinAbsoluteErrors() != null ? run.getMinAbsoluteErrors() : 5;

        // Карта siteName → cityId config для сайтов с парсер-фильтром
        Map<String, ZoomosCityId> cityIdBySite = allCityIds.stream()
                .filter(c -> c.getParserInclude() != null && !c.getParserInclude().isBlank())
                .collect(Collectors.toMap(ZoomosCityId::getSiteName, c -> c, (a, b) -> a));

        // Предзагружаем baseline-записи текущего run одним запросом (как в checkResults контроллере)
        boolean hasBaseline = run.getBaselineDays() != null && run.getBaselineDays() > 0;
        Map<String, List<ZoomosParsingStats>> baselineByKey = new LinkedHashMap<>();
        if (hasBaseline) {
            List<ZoomosParsingStats> baselineStatsList = parsingStatsRepository
                    .findByCheckRunIdAndIsBaselineTrueOrderByStartTimeDesc(run.getId());
            for (ZoomosParsingStats s : baselineStatsList) {
                String key = s.getSiteName() + "|" + (s.getCityName() != null ? s.getCityName() : "")
                        + "|" + (s.getAddressId() != null ? s.getAddressId() : "");
                baselineByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
            }
        }

        // Оцениваем каждую группу site+city (только завершённые группы из grouped)
        for (Map.Entry<String, List<ZoomosParsingStats>> entry : grouped.entrySet()) {
            List<ZoomosParsingStats> group = entry.getValue();
            // Сортируем по startTime (хронологически)
            group.sort(Comparator.comparing(
                    s -> s.getStartTime() != null ? s.getStartTime() : ZonedDateTime.now(),
                    Comparator.naturalOrder()));

            String groupKey = entry.getKey();
            String siteName = groupKey.split("\\|")[0];
            ZoomosCityId cidConfig = cityIdBySite.get(siteName);

            String status;
            if (cidConfig != null) {
                // Парсер-фильтр: completeness-check, как в контроллере.
                // Расширяем группу in-progress stats того же сайта — зеркалируем логику checkResults.
                List<ZoomosParsingStats> groupExt = new ArrayList<>(group);
                groupExt.addAll(inProgressBySite.getOrDefault(siteName, Collections.emptyList()));
                List<Map<String, Object>> incomplete = checkParserCompleteness(
                        groupExt, cidConfig.getParserInclude(), cidConfig.getParserIncludeMode());
                status = (incomplete == null || incomplete.isEmpty()) ? "OK" : "ERROR";
            } else {
                boolean ignoreStock = group.get(0).getSiteName() != null
                        && ignoreStockSites.contains(group.get(0).getSiteName());
                MedianStats baseline = null;
                if (hasBaseline) {
                    String siteForBl = group.get(0).getSiteName();
                    String cityForBl = group.get(0).getCityName();
                    String addrForBl = group.get(0).getAddressId();
                    String blKey = buildBaselineKey(siteForBl, cityForBl, addrForBl);
                    baseline = computeBaselineMedian(baselineByKey.getOrDefault(blKey, Collections.emptyList()));
                }
                status = evaluateGroup(group, run.getDropThreshold(), run.getErrorGrowthThreshold(),
                        minAbsErrors, ignoreStock, baseline, run.getShop().getShopName());
            }
            switch (status) {
                case "ERROR": error++; break;
                case "WARNING": warning++; break;
                default: ok++; break;
            }
        }

        run.setOkCount(ok);
        run.setWarningCount(warning);
        run.setErrorCount(error);
        run.setNotFoundCount(notFound);
    }

    // =========================================================================
    // Медианный baseline
    // =========================================================================

    /**
     * Медиана ключевых метрик по набору baseline-записей.
     */
    public record MedianStats(Integer inStock, Integer totalProducts, Integer durationMinutes) {}

    /**
     * Вычисляет медианные значения метрик по списку baseline-записей.
     */
    public MedianStats computeBaselineMedian(List<ZoomosParsingStats> baseline) {
        if (baseline == null || baseline.isEmpty()) return new MedianStats(null, null, null);
        Integer medInStock = median(baseline.stream()
                .map(ZoomosParsingStats::getInStock).filter(Objects::nonNull).sorted().toList());
        Integer medTotal = median(baseline.stream()
                .map(ZoomosParsingStats::getTotalProducts).filter(Objects::nonNull).sorted().toList());
        Integer medDuration = median(baseline.stream()
                .map(ZoomosParsingStats::getParsingDurationMinutes).filter(Objects::nonNull).sorted().toList());
        return new MedianStats(medInStock, medTotal, medDuration);
    }

    private static Integer median(List<Integer> sorted) {
        if (sorted.isEmpty()) return null;
        int n = sorted.size();
        if (n % 2 == 0) return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
        return sorted.get(n / 2);
    }

    /**
     * Единственная точка входа для оценки группы выкачек.
     * Определяет статус (OK/WARNING/ERROR) и формирует issue-сообщения за один проход.
     * Сортировка groupа: ASC по startTime (от старых к новым).
     *
     * @param ignoreStock       если true — inStock-анализ пропускается (сайт без данных о наличии)
     * @param minAbsoluteErrors минимальное абсолютное число ошибок для срабатывания варнинга
     * @param baseline          медиана за baseline-период; null → сравнение с предпоследней записью
     * @param shopName          имя магазина (для issue-записей)
     */
    public GroupEvalResult evaluateAndBuildIssues(List<ZoomosParsingStats> sortedGroup,
                                                   int dropThreshold, int errorGrowthThreshold,
                                                   int minAbsoluteErrors, boolean ignoreStock,
                                                   MedianStats baseline, String shopName) {
        if (sortedGroup == null || sortedGroup.isEmpty()) return GroupEvalResult.ok();

        ZoomosParsingStats newest = sortedGroup.get(sortedGroup.size() - 1);
        ZoomosParsingStats prev   = sortedGroup.size() >= 2 ? sortedGroup.get(sortedGroup.size() - 2) : null;

        String siteName    = newest.getSiteName();
        String cityName    = newest.getCityName();
        String checkType   = newest.getCheckType() != null ? newest.getCheckType().name() : "ITEM";
        String addressId   = newest.getAddressId();
        String addressName = newest.getAddressName();
        String cityId = cityName != null && cityName.contains(" - ")
                ? cityName.substring(0, cityName.indexOf(" - ")).trim()
                : (cityName != null ? cityName.trim() : "");

        List<Map<String, Object>> issues = new ArrayList<>();
        boolean hasWarning = false;
        boolean hasError   = false;

        boolean alwaysZeroProducts = sortedGroup.stream()
                .allMatch(s -> s.getTotalProducts() == null || s.getTotalProducts() == 0);
        boolean allFullyComplete = sortedGroup.stream()
                .allMatch(s -> s.getCompletionPercent() != null && s.getCompletionPercent() >= 100);

        // WARNING: 100% выкачка, но нет товаров совсем
        if (alwaysZeroProducts && allFullyComplete) {
            issues.add(makeIssueMap(siteName, cityName, cityId, addressId, addressName, checkType, shopName,
                    "WARNING", "100% выкачка, нет товаров — нужна проверка"));
            hasWarning = true;
        }

        // --- Одиночная запись без baseline ---
        if (prev == null && baseline == null) {
            if (newest.getCompletionPercent() != null && newest.getCompletionPercent() < 100) {
                hasWarning = true; // статус WARNING, без issue-сообщения
            }
            if (!ignoreStock
                    && newest.getInStock() != null && newest.getInStock() == 0
                    && newest.getTotalProducts() != null && newest.getTotalProducts() > 0) {
                issues.add(makeIssueMap(siteName, cityName, cityId, addressId, addressName, checkType, shopName,
                        "WARNING", "В наличии: 0 из " + newest.getTotalProducts() + " товаров (первая запись)"));
                hasWarning = true;
            }
            return new GroupEvalResult(hasWarning ? "WARNING" : "OK", issues);
        }

        // --- Несколько записей или есть baseline ---

        // Завершённость выкачки (статус без issue-сообщения)
        if (newest.getCompletionPercent() != null && newest.getCompletionPercent() < 100) {
            hasWarning = true;
        }

        double dropFraction      = dropThreshold / 100.0;
        double errGrowthFraction = errorGrowthThreshold / 100.0;

        boolean alwaysZeroInStock = sortedGroup.stream()
                .allMatch(s -> s.getInStock() == null || s.getInStock() == 0);

        if (!ignoreStock && !alwaysZeroInStock) {
            // PRIMARY: inStock — сравниваем с медианой или предыдущей записью
            Integer refInStock   = (baseline != null && baseline.inStock() != null)
                    ? baseline.inStock()
                    : (prev != null ? prev.getInStock() : null);
            boolean usingBaseline = baseline != null && baseline.inStock() != null;
            Integer newStock      = newest.getInStock();

            if (refInStock != null && refInStock > 0 && newStock != null) {
                if (newStock == 0) {
                    int lastNonZero = usingBaseline ? refInStock
                            : sortedGroup.subList(0, sortedGroup.size() - 1).stream()
                                    .filter(s -> s.getInStock() != null && s.getInStock() > 0)
                                    .mapToInt(ZoomosParsingStats::getInStock).max().orElse(refInStock);
                    String prefix = usingBaseline ? String.format("[медиана: %d]", refInStock) : String.valueOf(lastNonZero);
                    issues.add(makeIssueMap(siteName, cityName, cityId, addressId, addressName, checkType, shopName,
                            "ERROR", String.format("В наличии: %s → 0 (−100%%)", prefix)));
                    hasError = true;
                } else {
                    double drop = (double)(refInStock - newStock) / refInStock;
                    if (drop > dropFraction) {
                        String refLabel = usingBaseline ? String.format("[медиана: %d]", refInStock) : String.valueOf(refInStock);
                        issues.add(makeIssueMap(siteName, cityName, cityId, addressId, addressName, checkType, shopName,
                                "ERROR", String.format("Падение 'В наличии': %s → %d (−%.0f%%)", refLabel, newStock, drop * 100)));
                        hasError = true;
                    }
                }
            }

        } else {
            // FALLBACK: totalProducts (ignoreStock или всегда нули в inStock)
            Integer refTotal      = (baseline != null && baseline.totalProducts() != null)
                    ? baseline.totalProducts()
                    : (prev != null ? prev.getTotalProducts() : null);
            boolean usingBaseline = baseline != null && baseline.totalProducts() != null;
            Integer newTotal      = newest.getTotalProducts();

            if (refTotal != null && refTotal > 0 && newTotal != null) {
                double drop = (double)(refTotal - newTotal) / refTotal;
                if (drop > dropFraction) {
                    String refLabel = usingBaseline ? String.format("[медиана: %d]", refTotal) : String.valueOf(refTotal);
                    issues.add(makeIssueMap(siteName, cityName, cityId, addressId, addressName, checkType, shopName,
                            "ERROR", String.format("Падение товаров: %s → %d (−%.0f%%)", refLabel, newTotal, drop * 100)));
                    hasError = true;
                }
            }

            // Ошибки парсинга (только если inStock недоступен)
            int prevErrors = prev != null && prev.getErrorCount() != null ? prev.getErrorCount() : 0;
            int newErrors  = newest.getErrorCount() != null ? newest.getErrorCount() : 0;
            if (newErrors >= minAbsoluteErrors) {
                if (prevErrors > 0 && newErrors > prevErrors) {
                    double growth = (double)(newErrors - prevErrors) / prevErrors;
                    if (growth > errGrowthFraction) {
                        issues.add(makeIssueMap(siteName, cityName, cityId, addressId, addressName, checkType, shopName,
                                "WARNING", String.format("Рост ошибок: %d → %d (+%.0f%%)", prevErrors, newErrors, growth * 100)));
                        hasWarning = true;
                    }
                } else if (prevErrors == 0) {
                    issues.add(makeIssueMap(siteName, cityName, cityId, addressId, addressName, checkType, shopName,
                            "WARNING", String.format("Ошибки парсинга: 0 → %d", newErrors)));
                    hasWarning = true;
                }
            }

            // alwaysZeroInStock + есть товары — предупреждаем
            if (alwaysZeroInStock && !ignoreStock) {
                boolean hasAnyProducts = sortedGroup.stream()
                        .anyMatch(s -> s.getTotalProducts() != null && s.getTotalProducts() > 0);
                if (hasAnyProducts) {
                    issues.add(makeIssueMap(siteName, cityName, cityId, addressId, addressName, checkType, shopName,
                            "WARNING", "В наличии: всегда 0 — нужна проверка"));
                    hasWarning = true;
                }
            }
        }

        // Скорость выкачки (косвенный признак, только если нет других проблем и baseline доступен)
        if (!hasError && !hasWarning
                && baseline != null && baseline.durationMinutes() != null
                && newest.getParsingDurationMinutes() != null) {
            int refDur = baseline.durationMinutes();
            int curDur = newest.getParsingDurationMinutes();
            if (refDur > 0 && (double) curDur > refDur * 1.5) {
                issues.add(makeIssueMap(siteName, cityName, cityId, addressId, addressName, checkType, shopName,
                        "WARNING", String.format("Медленная выкачка: %d мин (базовый: %d мин)", curDur, refDur)));
                hasWarning = true;
            }
        }

        String status = hasError ? "ERROR" : (hasWarning ? "WARNING" : "OK");
        return new GroupEvalResult(status, issues);
    }

    /** Делегат для обратной совместимости внутри сервиса (updateRunSummary). */
    public String evaluateGroup(List<ZoomosParsingStats> sortedGroup,
                                 int dropThreshold, int errorGrowthThreshold,
                                 int minAbsoluteErrors, boolean ignoreStock,
                                 MedianStats baseline, String shopName) {
        return evaluateAndBuildIssues(sortedGroup, dropThreshold, errorGrowthThreshold,
                minAbsoluteErrors, ignoreStock, baseline, shopName).status();
    }

    public static String buildBaselineKey(String siteName, String cityName, String addressId) {
        return siteName + "|" + (cityName != null ? cityName : "")
                + "|" + (addressId != null ? addressId : "");
    }

    private Map<String, Object> makeIssueMap(String siteName, String cityName, String cityId,
                                              String addressId, String addressName,
                                              String checkType, String shopName,
                                              String type, String message) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("site", siteName);
        issue.put("city", cityName);
        issue.put("cityId", cityId);
        issue.put("addressId", addressId);
        issue.put("addressName", addressName);
        issue.put("checkType", checkType);
        issue.put("shopName", shopName);
        issue.put("type", type);
        issue.put("message", message);
        return issue;
    }

    // =========================================================================
    // WebSocket прогресс
    // =========================================================================

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
    // Авторизация — делегируем в общую инфраструктуру куки
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

    // =========================================================================
    // Проверка завершённости парсеров по фильтру (AND/OR)
    // =========================================================================

    /**
     * Проверяет, завершены ли все обязательные парсеры в группе.
     * <ul>
     *   <li>OR-режим: хотя бы один из перечисленных паттернов имеет row с completionPercent&ge;100.</li>
     *   <li>AND-режим: каждый паттерн обязан иметь хотя бы одну row с completionPercent&ge;100.</li>
     * </ul>
     * @return null — фильтр не задан; пустой список — всё OK; непустой — незавершённые паттерны с метриками.
     */
    public List<Map<String, Object>> checkParserCompleteness(
            List<ZoomosParsingStats> group, String parserInclude, String parserIncludeMode) {

        if (parserInclude == null || parserInclude.isBlank()) return null;

        List<String> patterns = Arrays.stream(parserInclude.split(";"))
                .map(String::trim).filter(p -> !p.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        if (patterns.isEmpty()) return null;

        boolean andMode = "AND".equalsIgnoreCase(parserIncludeMode);

        if (andMode) {
            // AND: каждый паттерн должен дать хотя бы одну завершённую row
            List<Map<String, Object>> incomplete = new ArrayList<>();
            for (String pattern : patterns) {
                List<ZoomosParsingStats> matching = group.stream()
                        .filter(s -> s.getParserDescription() != null
                                && s.getParserDescription().toLowerCase().contains(pattern))
                        .collect(Collectors.toList());
                boolean anyComplete = matching.stream()
                        .anyMatch(s -> s.getCompletionPercent() != null && s.getCompletionPercent() >= 100);
                if (!anyComplete) {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("pattern", pattern);
                    if (matching.isEmpty()) {
                        info.put("noData", true);
                    } else {
                        matching.stream()
                                .max(Comparator.comparing(s -> s.getCompletionPercent() != null ? s.getCompletionPercent() : 0))
                                .ifPresent(best -> {
                                    info.put("completion", best.getCompletionTotal());
                                    info.put("total", best.getTotalProducts());
                                    info.put("inStock", best.getInStock());
                                    info.put("errors", best.getErrorCount());
                                    info.put("description", best.getParserDescription());
                                    info.put("updatedTime", best.getUpdatedTime());
                                });
                    }
                    incomplete.add(info);
                }
            }
            return incomplete;
        } else {
            // OR: хотя бы одна row из любого паттерна завершена на 100%
            boolean anyComplete = group.stream()
                    .filter(s -> s.getParserDescription() != null
                            && patterns.stream().anyMatch(p -> s.getParserDescription().toLowerCase().contains(p)))
                    .anyMatch(s -> s.getCompletionPercent() != null && s.getCompletionPercent() >= 100);
            if (anyComplete) return Collections.emptyList();

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("pattern", String.join(", ", patterns));
            info.put("orMode", true);
            group.stream()
                    .filter(s -> s.getParserDescription() != null
                            && patterns.stream().anyMatch(p -> s.getParserDescription().toLowerCase().contains(p)))
                    .max(Comparator.comparing(s -> s.getCompletionPercent() != null ? s.getCompletionPercent() : 0))
                    .ifPresentOrElse(best -> {
                        info.put("completion", best.getCompletionTotal());
                        info.put("total", best.getTotalProducts());
                        info.put("inStock", best.getInStock());
                        info.put("errors", best.getErrorCount());
                        info.put("description", best.getParserDescription());
                        info.put("updatedTime", best.getUpdatedTime());
                    }, () -> info.put("noData", true));
            return List.of(info);
        }
    }
}
