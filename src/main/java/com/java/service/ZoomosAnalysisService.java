package com.java.service;

import com.java.config.ZoomosConfig;
import com.java.dto.zoomos.*;
import com.java.dto.zoomos.SparklinePoint;
import com.java.model.entity.*;
import com.java.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZoomosAnalysisService {

    private final ZoomosCheckRunRepository checkRunRepository;
    private final ZoomosParsingStatsRepository parsingStatsRepository;
    private final ZoomosCityIdRepository cityIdRepository;
    private final ZoomosCheckProfileRepository profileRepository;
    private final ZoomosCityNameRepository cityNameRepository;
    private final ZoomosConfig zoomosConfig;
    private final ZoomosKnownSiteRepository knownSiteRepository;

    private static final DateTimeFormatter TIME_FMT     = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final ZonedDateTime EPOCH = ZonedDateTime.parse("1970-01-01T00:00:00Z");

    // =========================================================================
    // Публичный метод
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ZoomosSiteResult> analyze(Long checkRunId, Long profileId,
                                          ZonedDateTime deadline, int stallMinutes) {
        ZoomosCheckRun run = checkRunRepository.findByIdWithShop(checkRunId)
                .orElseThrow(() -> new IllegalArgumentException("Проверка не найдена: " + checkRunId));

        List<ZoomosParsingStats> allStats = parsingStatsRepository
                .findByCheckRunIdAndIsBaselineFalseOrderBySiteNameAscCityNameAsc(checkRunId);
        List<ZoomosParsingStats> baselineStats = parsingStatsRepository
                .findByCheckRunIdAndIsBaselineTrueOrderByStartTimeDesc(checkRunId);

        log.info("Total stats loaded for run {}: {} records (baseline: {})",
                checkRunId, allStats.size(), baselineStats.size());
        if (log.isDebugEnabled()) {
            allStats.stream().map(ZoomosParsingStats::getSiteName).distinct().sorted()
                    .forEach(s -> log.debug("  loaded site: {}", s));
        }

        int dropThreshold        = run.getDropThreshold()          != null ? run.getDropThreshold()          : 10;
        int errorGrowthThreshold = run.getErrorGrowthThreshold()   != null ? run.getErrorGrowthThreshold()   : 30;
        int trendDropThreshold   = run.getTrendDropThreshold()     != null ? run.getTrendDropThreshold()     : 30;
        int minAbsoluteErrors    = run.getMinAbsoluteErrors()      != null ? run.getMinAbsoluteErrors()      : 5;

        Map<String, String> cityNamesMap = cityNameRepository.findAll().stream()
                .collect(Collectors.toMap(ZoomosCityName::getCityId, ZoomosCityName::getCityName, (a, b) -> a));

        Map<String, ZoomosKnownSite> knownSiteMap = knownSiteRepository.findAll().stream()
                .collect(Collectors.toMap(ZoomosKnownSite::getSiteName, s -> s, (a, b) -> a));

        Map<String, SiteConfig> siteConfigs = profileId != null
                ? buildProfileConfigs(profileId, knownSiteMap)
                : buildLegacyConfigs(run.getShop().getId(), knownSiteMap);

        Map<String, ZoomosCityId> cityIdMap = cityIdRepository.findByShopIdOrderBySiteName(run.getShop().getId())
                .stream().collect(Collectors.toMap(ZoomosCityId::getSiteName, c -> c, (a, b) -> a));

        List<ZoomosSiteResult> results = new ArrayList<>();
        Set<String> processedSites = new HashSet<>();

        // Обрабатываем сконфигурированные сайты
        for (Map.Entry<String, SiteConfig> entry : siteConfigs.entrySet()) {
            String siteName = entry.getKey();
            SiteConfig config = entry.getValue();

            List<ZoomosParsingStats> siteStats     = findStatsBySiteName(allStats, siteName);
            List<ZoomosParsingStats> siteBaseline  = findStatsBySiteName(baselineStats, siteName);

            results.add(buildSiteResult(siteName, siteStats, siteBaseline, config, deadline, stallMinutes,
                    dropThreshold, errorGrowthThreshold, trendDropThreshold, minAbsoluteErrors, cityNamesMap, run,
                    knownSiteMap.get(siteName), cityIdMap));
            processedSites.add(siteName);
        }

        // Сайты без конфигурации
        Map<String, List<ZoomosParsingStats>> bySite = allStats.stream()
                .collect(Collectors.groupingBy(ZoomosParsingStats::getSiteName));
        for (Map.Entry<String, List<ZoomosParsingStats>> entry : bySite.entrySet()) {
            String siteName = entry.getKey();
            if (processedSites.contains(siteName)) continue;
            List<ZoomosParsingStats> siteBaseline = findStatsBySiteName(baselineStats, siteName);
            SiteConfig defaultConfig = new SiteConfig(siteName, null, null, null, "OR", null, null);
            results.add(buildSiteResult(siteName, entry.getValue(), siteBaseline, defaultConfig, deadline,
                    stallMinutes, dropThreshold, errorGrowthThreshold, trendDropThreshold, minAbsoluteErrors, cityNamesMap, run,
                    knownSiteMap.get(siteName), cityIdMap));
        }

        results.sort(Comparator.comparingInt(r -> r.getStatus().priority));
        return results;
    }

    // =========================================================================
    // Анализ одного сайта
    // =========================================================================

    private ZoomosSiteResult buildSiteResult(
            String siteName,
            List<ZoomosParsingStats> siteStats,
            List<ZoomosParsingStats> siteBaseline,
            SiteConfig config,
            ZonedDateTime deadline,
            int stallMinutes,
            int dropThreshold,
            int errorGrowthThreshold,
            int trendDropThreshold,
            int minAbsoluteErrors,
            Map<String, String> cityNamesMap,
            ZoomosCheckRun run,
            ZoomosKnownSite knownSite,
            Map<String, ZoomosCityId> cityIdMap) {

        boolean ignoreStock = knownSite != null && knownSite.isIgnoreStock();

        String masterRaw = config.masterCityId();
        String masterId = masterRaw != null ? masterRaw.trim().split("[\\s\\-]+")[0].trim() : null;
        Set<String> expectedCities = (masterId != null && !masterId.isBlank())
                ? Set.of(masterId)
                : parseExpectedCities(config.cityIds());
        List<SiteIssue> siteIssues = new ArrayList<>();
        List<CityResult> cityResults = new ArrayList<>();

        // ── Шаг A: per-city анализ ──────────────────────────────────────────
        if (expectedCities.isEmpty()) {
            List<ZoomosParsingStats> filtered = applyParserFilter(siteStats, config);
            if (filtered.isEmpty() && !siteStats.isEmpty() && config.parserInclude() != null && !config.parserInclude().isBlank()) {
                siteIssues.add(new SiteIssue(StatusReason.CATEGORY_MISSING, buildCategoryMissingMsg(config.parserInclude(), siteStats)));
            }
            cityResults.add(analyzeCityGroup(null, null,
                    filtered.isEmpty() ? siteStats : filtered, siteBaseline,
                    deadline, stallMinutes, dropThreshold, minAbsoluteErrors, ignoreStock,
                    run.getDateTo(), run.getDateFrom().equals(run.getDateTo())));
        } else {
            // Применяем фильтр категорий
            boolean needsParserFilter = config.parserInclude() != null && !config.parserInclude().isBlank();
            if (needsParserFilter) {
                boolean anyMatch = siteStats.stream()
                        .anyMatch(s -> matchesParserInclude(s.getParserDescription(), config.parserInclude(), config.parserIncludeMode()));
                if (!anyMatch && !siteStats.isEmpty()) {
                    siteIssues.add(new SiteIssue(StatusReason.CATEGORY_MISSING, buildCategoryMissingMsg(config.parserInclude(), siteStats)));
                }
            }
            for (String cId : expectedCities) {
                String cName = cityNamesMap.getOrDefault(cId, cId);
                List<ZoomosParsingStats> cStats = findStatsByCityId(siteStats, siteName, cId);
                log.info("stats for {} city {}: {} records", siteName, cId, cStats.size());
                if (cStats.isEmpty() && log.isDebugEnabled()) {
                    siteStats.forEach(s -> log.debug(
                            "  raw stat: cityName={}, isFinished={}, completionPercent={}, parsingDate={}",
                            s.getCityName(), s.getIsFinished(), s.getCompletionPercent(), s.getParsingDate()));
                }
                List<ZoomosParsingStats> cBl = findStatsByCityId(siteBaseline, siteName, cId);
                List<ZoomosParsingStats> cBlInPeriod = cBl.stream()
                        .filter(s -> s.getParsingDate() != null
                                && !s.getParsingDate().isBefore(run.getDateFrom())
                                && !s.getParsingDate().isAfter(run.getDateTo()))
                        .collect(Collectors.toList());
                List<ZoomosParsingStats> cFiltered = applyParserFilter(cStats, config);
                List<ZoomosParsingStats> effectiveStats = new ArrayList<>(cFiltered.isEmpty() ? cStats : cFiltered);
                effectiveStats.addAll(cBlInPeriod);
                cityResults.add(analyzeCityGroup(cId, cName, effectiveStats, cBl,
                        deadline, stallMinutes, dropThreshold, minAbsoluteErrors, ignoreStock,
                        run.getDateTo(), run.getDateFrom().equals(run.getDateTo())));
            }
            // CITIES_MISSING — "получено" = только OK города
            long missingCount = 0, okCount = 0;
            for (CityResult cr : cityResults) {
                if (cr.issues().stream().anyMatch(i -> i.reason() == StatusReason.NOT_FOUND)) missingCount++;
                if (cr.status() == ZoomosResultLevel.OK) okCount++;
            }
            if (missingCount > 0) {
                siteIssues.add(new SiteIssue(StatusReason.CITIES_MISSING,
                        "Не все города выкачались: ожидалось " + expectedCities.size() +
                        ", получено " + okCount));
            }
        }

        // ── Шаг B: проверка аккаунта ────────────────────────────────────────
        if (config.accountFilter() != null && !config.accountFilter().isBlank() && !siteStats.isEmpty()) {
            boolean hasAccount = siteStats.stream().anyMatch(s -> s.getAccountName() != null
                    && s.getAccountName().toLowerCase().contains(config.accountFilter().toLowerCase()));
            if (!hasAccount) {
                siteIssues.add(new SiteIssue(StatusReason.ACCOUNT_MISSING,
                        "Нет выкачки с нужным аккаунтом (" + config.accountFilter() + ")"));
            }
        }

        // ── Шаг C: агрегированные метрики и тренды ──────────────────────────
        // При мастер-городе метрики считаются только по нему, иначе по всем городам
        List<ZoomosParsingStats> metricsStats    = (masterId != null && !masterId.isBlank())
                ? findStatsByCityId(siteStats,    siteName, masterId) : siteStats;
        List<ZoomosParsingStats> metricsBaseline = (masterId != null && !masterId.isBlank())
                ? findStatsByCityId(siteBaseline, siteName, masterId) : siteBaseline;

        ZoomosParsingStats latestStat = metricsStats.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsFinished()))
                .max(Comparator.comparing(s -> s.getStartTime() != null ? s.getStartTime() : EPOCH))
                .orElse(null);

        Double baselineInStock   = computeMedian(metricsBaseline, s -> s.getInStock() != null ? (double) s.getInStock() : null);
        Double baselineErrorRate = computeMedian(metricsBaseline, s ->
                (s.getTotalProducts() != null && s.getTotalProducts() > 0 && s.getErrorCount() != null)
                        ? (double) s.getErrorCount() / s.getTotalProducts() : null);
        Double baselineSpeed     = computeMedian(metricsBaseline, s ->
                (s.getTotalProducts() != null && s.getTotalProducts() > 100 && s.getParsingDurationMinutes() != null)
                        ? (double) s.getParsingDurationMinutes() / s.getTotalProducts() * 1000 : null);

        if (latestStat != null && baselineErrorRate != null) {
            Integer latestErrors = latestStat.getErrorCount();
            Integer latestTotal  = latestStat.getTotalProducts();
            if (latestErrors != null && latestTotal != null && latestTotal > 0 && latestErrors >= minAbsoluteErrors) {
                double currentRate = (double) latestErrors / latestTotal;
                if (currentRate > baselineErrorRate * (1 + errorGrowthThreshold / 100.0)) {
                    double pct = baselineErrorRate > 0 ? (currentRate - baselineErrorRate) / baselineErrorRate * 100 : 100;
                    siteIssues.add(new SiteIssue(StatusReason.ERROR_GROWTH,
                            String.format("Ошибок парсинга больше baseline на %.0f%%", pct)));
                }
            }
        }
        List<ZoomosParsingStats> trendData = new ArrayList<>(pickBestPerDayList(metricsBaseline));
        if (latestStat != null) trendData.add(latestStat);
        trendData.sort(Comparator.comparing(s -> s.getParsingDate() != null ? s.getParsingDate() : java.time.LocalDate.MIN));
        List<Integer> inStockValues = trendData.stream()
                .filter(s -> s.getInStock() != null).map(ZoomosParsingStats::getInStock).collect(Collectors.toList());
        int consDrop = countConsecutiveDrop(inStockValues);
        if (!ignoreStock && consDrop >= 3) {
            int lastIdx = inStockValues.size() - 1;
            double firstVal = inStockValues.get(lastIdx - consDrop);
            double lastVal  = inStockValues.get(lastIdx);
            double totalDrop = firstVal > 0 ? (firstVal - lastVal) / firstVal * 100 : 0;
            if (totalDrop >= trendDropThreshold / 2.0) {
                siteIssues.add(new SiteIssue(StatusReason.STOCK_TREND_DOWN, "inStock снижается " + consDrop + " дней подряд"));
            }
        }
        // SPEED_TREND проверяется первым — если найден, SPEED_SPIKE не добавляется
        List<Double> speedByDay = medianSpeedPerDay(metricsBaseline).values().stream()
                .filter(Objects::nonNull).collect(Collectors.toList());
        boolean speedTrendFound = false;
        if (speedByDay.size() >= 3 && isConsecutivelyIncreasing(speedByDay)) {
            double first = speedByDay.get(speedByDay.size() - 3);
            double last  = speedByDay.get(speedByDay.size() - 1);
            siteIssues.add(new SiteIssue(StatusReason.SPEED_TREND,
                    String.format("Выкачка замедляется: %.0f → %.0f мин/1000 тов", first, last)));
            speedTrendFound = true;
        }
        if (!speedTrendFound && baselineSpeed != null) {
            List<ZoomosParsingStats> finishedToday = metricsStats.stream()
                    .filter(s -> Boolean.TRUE.equals(s.getIsFinished())
                            || (s.getCompletionPercent() != null && s.getCompletionPercent() >= 100))
                    .collect(Collectors.toList());
            Double curSpeed = computeMedian(finishedToday, s ->
                    (s.getTotalProducts() != null && s.getTotalProducts() > 100 && s.getParsingDurationMinutes() != null)
                            ? s.getParsingDurationMinutes().doubleValue() / s.getTotalProducts() * 1000 : null);
            if (curSpeed != null && curSpeed > baselineSpeed * (1 + trendDropThreshold / 100.0)) {
                siteIssues.add(new SiteIssue(StatusReason.SPEED_SPIKE,
                        String.format("Разовое замедление: %.0f мин/1000 тов (baseline %.0f мин/1000 тов)", curSpeed, baselineSpeed)));
            }
        }

        // ── Шаг D: поднятие CRITICAL/WARNING из городов на уровень сайта ────
        EnumSet<StatusReason> liftedReasons = EnumSet.noneOf(StatusReason.class);
        for (SiteIssue si : siteIssues) liftedReasons.add(si.reason());
        for (CityResult cr : cityResults) {
            for (SiteIssue issue : cr.issues()) {
                if ((issue.reason().level == ZoomosResultLevel.CRITICAL || issue.reason().level == ZoomosResultLevel.WARNING)
                        && issue.reason() != StatusReason.CITIES_MISSING
                        && !liftedReasons.contains(issue.reason())) {
                    siteIssues.add(issue);
                    liftedReasons.add(issue.reason());
                }
            }
        }

        // ── Шаг E: итоговый статус ──────────────────────────────────────────
        ZoomosResultLevel worstCityStatus = cityResults.stream()
                .map(CityResult::status)
                .min(Comparator.comparingInt(l -> l.priority))
                .orElse(ZoomosResultLevel.OK);
        ZoomosResultLevel siteStatus = siteIssues.stream()
                .map(i -> i.reason().level)
                .min(Comparator.comparingInt(l -> l.priority))
                .orElse(worstCityStatus);
        if (worstCityStatus.priority < siteStatus.priority) siteStatus = worstCityStatus;

        // ── Шаг F: поля для одиночного города ──────────────────────────────
        String resultCityId   = cityResults.size() == 1 ? cityResults.get(0).cityId()   : null;
        String resultCityName = cityResults.size() == 1 ? cityResults.get(0).cityName() : null;
        ZonedDateTime estFinish  = cityResults.size() == 1 ? cityResults.get(0).estimatedFinish()          : null;
        Boolean estReliable      = cityResults.size() == 1 ? cityResults.get(0).estimatedFinishReliable()  : null;
        boolean stalled          = cityResults.stream().anyMatch(CityResult::isStalled);

        String checkType   = siteStats.stream().findFirst()
                .map(s -> s.getCheckType() != null ? s.getCheckType().name() : null).orElse(null);
        String accountName = siteStats.stream().filter(s -> s.getAccountName() != null).findFirst()
                .map(ZoomosParsingStats::getAccountName).orElse(null);

        String shopParam = "API".equals(checkType) ? "-" : run.getShop().getShopName();
        String baseUrlNorm = zoomosConfig.getBaseUrlNormalized();
        String historyBaseUrl = baseUrlNorm.isBlank() ? null
                : baseUrlNorm + "/shops-parser/" + siteName + "/parsing-history";

        ZoomosCityId cityIdEntity = cityIdMap.get(siteName);
        Long cityIdsId           = cityIdEntity != null ? cityIdEntity.getId()              : null;
        boolean hasConfigIssue   = cityIdEntity != null && cityIdEntity.isHasConfigIssue();
        String configIssueType   = cityIdEntity != null ? cityIdEntity.getConfigIssueType() : null;
        String configIssueNote   = cityIdEntity != null ? cityIdEntity.getConfigIssueNote() : null;

        List<CityResult> cityResultsWithConfig = cityResults.stream()
                .map(cr -> new CityResult(cr.cityId(), cr.cityName(), cr.status(), cr.inStock(),
                        cr.issues(), cr.estimatedFinish(), cr.estimatedFinishReliable(), cr.isStalled(),
                        cr.baselineInStock(), cr.inStockDelta(), cr.inStockDeltaPercent(),
                        cityIdsId, hasConfigIssue, configIssueType, configIssueNote,
                        cr.lastKnownDate(), cr.lastKnownInStock(), cr.lastKnownCompletionPercent(), cr.lastKnownIsStalled(), cr.lastKnownUpdatedTime()))
                .collect(Collectors.toList());

        List<CityResult> inProgressCities = cityResultsWithConfig.stream()
                .filter(cr -> cr.status() == ZoomosResultLevel.IN_PROGRESS)
                .collect(Collectors.toList());

        List<ZoomosParsingStats> allForHistory = new ArrayList<>(siteBaseline);
        allForHistory.addAll(siteStats);

        List<SparklinePoint> errorHistory = computeErrorHistory(allForHistory);
        log.debug("errorHistory for {}: {} points", siteName, errorHistory.size());
        return ZoomosSiteResult.builder()
                .siteName(siteName)
                .cityId(resultCityId)
                .cityName(resultCityName)
                .accountName(accountName)
                .checkType(checkType)
                .status(siteStatus)
                .statusReasons(sortedIssues(siteIssues))
                .latestStat(latestStat)
                .baselineInStock(baselineInStock)
                .baselineErrorRate(baselineErrorRate)
                .baselineSpeedMinsPer1000(baselineSpeed)
                .estimatedFinish(estFinish)
                .estimatedFinishReliable(estReliable)
                .isStalled(stalled)
                .cityResults(cityResultsWithConfig)
                .inProgressCities(inProgressCities)
                .inStockHistory(computeInStockHistory(allForHistory))
                .errorHistory(errorHistory)
                .speedHistory(computeSpeedHistory(allForHistory))
                .shopParam(shopParam)
                .historyBaseUrl(historyBaseUrl)
                .ignoreStock(ignoreStock)
                .masterCityId(config.masterCityId())
                .cityIdsId(cityIdsId)
                .hasConfigIssue(hasConfigIssue)
                .configIssueType(configIssueType)
                .configIssueNote(configIssueNote)
                .siteId(knownSite != null ? knownSite.getId() : null)
                .isPriority(knownSite != null && knownSite.isPriority())
                .itemPriceConfigured(knownSite != null ? knownSite.getItemPriceConfigured() : null)
                .equalPrices(knownSite != null ? knownSite.getCitiesEqualPrices() : null)
                .equalPricesCheckedAt(knownSite != null && knownSite.getCitiesEqualPricesCheckedAt() != null
                        ? knownSite.getCitiesEqualPricesCheckedAt().format(DATETIME_FMT) : null)
                .dateFrom(run.getDateFrom() != null ? run.getDateFrom().format(DATE_FMT) : null)
                .dateTo(run.getDateTo()   != null ? run.getDateTo().format(DATE_FMT)   : null)
                .build();
    }

    // =========================================================================
    // Per-city анализ (с багфиксом STALLED — break после нахождения)
    // =========================================================================

    private CityResult analyzeCityGroup(
            String cityId, String cityName,
            List<ZoomosParsingStats> stats,
            List<ZoomosParsingStats> baselineStats,
            ZonedDateTime deadline,
            int stallMinutes,
            int dropThreshold,
            int minAbsoluteErrors,
            boolean ignoreStock,
            java.time.LocalDate checkDate,
            boolean isSingleDay) {

        List<SiteIssue> issues = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now();

        // Последняя известная запись для подсказки при NOT_FOUND / STALLED
        // Ищем в обоих источниках: baseline + текущие stats (незавершённые тоже учитываем)
        ZoomosParsingStats lastKnownStat = Stream.concat(baselineStats.stream(), stats.stream())
                .filter(s -> s.getParsingDate() != null)
                .max(Comparator.comparing(ZoomosParsingStats::getParsingDate)
                        .thenComparing(s -> s.getUpdatedTime() != null ? s.getUpdatedTime() : EPOCH))
                .orElse(null);
        java.time.LocalDate lastKnownDate = lastKnownStat != null ? lastKnownStat.getParsingDate() : null;
        Integer lastKnownInStock = lastKnownStat != null ? lastKnownStat.getInStock() : null;
        Integer lastKnownCompletionPercent = lastKnownStat != null ? lastKnownStat.getCompletionPercent() : null;
        ZonedDateTime lastKnownUpdatedTime = lastKnownStat != null ? lastKnownStat.getUpdatedTime() : null;
        Boolean lastKnownIsStalled = (lastKnownUpdatedTime != null
                && !Boolean.TRUE.equals(lastKnownStat.getIsFinished()))
                ? Duration.between(lastKnownUpdatedTime, now).toMinutes() >= stallMinutes
                : null;

        if (stats.isEmpty()) {
            issues.add(new SiteIssue(StatusReason.NOT_FOUND, StatusReason.NOT_FOUND.messageTemplate));
            return new CityResult(cityId, cityName, ZoomosResultLevel.CRITICAL, null, sortedIssues(issues), null, null, false, null, null, null, null, null, null, null,
                    lastKnownDate, lastKnownInStock, lastKnownCompletionPercent, lastKnownIsStalled, lastKnownUpdatedTime);
        }

        // При однодневной проверке анализируем записи за checkDate +
        // незавершённые записи за предыдущий день (ночные выкачки)
        List<ZoomosParsingStats> dayStats;
        if (isSingleDay && checkDate != null) {
            java.time.LocalDate yesterday = checkDate.minusDays(1);
            dayStats = stats.stream()
                    .filter(s -> checkDate.equals(s.getParsingDate())
                            || (yesterday.equals(s.getParsingDate())
                                    && !Boolean.TRUE.equals(s.getIsFinished())
                                    && (s.getCompletionPercent() == null || s.getCompletionPercent() < 100)))
                    .collect(Collectors.toList());
        } else {
            dayStats = stats;
        }
        if (isSingleDay && dayStats.isEmpty()) {
            issues.add(new SiteIssue(StatusReason.NOT_FOUND, StatusReason.NOT_FOUND.messageTemplate));
            return new CityResult(cityId, cityName, ZoomosResultLevel.CRITICAL, null, sortedIssues(issues), null, null, false, null, null, null, null, null, null, null,
                    lastKnownDate, lastKnownInStock, lastKnownCompletionPercent, lastKnownIsStalled, lastKnownUpdatedTime);
        }

        String debugSite = stats.stream().map(ZoomosParsingStats::getSiteName).filter(Objects::nonNull).findFirst().orElse("");
        boolean stalledFound  = false;
        ZonedDateTime estFinish = null;
        Boolean estReliable     = null;

        List<ZoomosParsingStats> finished = dayStats.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsFinished())
                        || (s.getCompletionPercent() != null && s.getCompletionPercent() >= 100))
                .sorted(Comparator.comparing((ZoomosParsingStats s) ->
                        s.getFinishTime() != null ? s.getFinishTime() : EPOCH, Comparator.reverseOrder()))
                .collect(Collectors.toList());
        List<ZoomosParsingStats> inProgress = dayStats.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsFinished())
                        && (s.getCompletionPercent() == null || s.getCompletionPercent() < 100))
                .collect(Collectors.toList());

        ZoomosParsingStats latest;
        Integer latestInStock;
        Double baselineInStock = null;
        Integer inStockDelta = null;
        Integer inStockDeltaPercent = null;

        if (!finished.isEmpty()) {
            latest = pickBestPerDay(finished);
            latestInStock = latest != null ? latest.getInStock() : null;
            if (ignoreStock) {
                Integer total = latest != null ? latest.getTotalProducts() : null;
                if (total == null || total == 0) {
                    issues.add(new SiteIssue(StatusReason.NO_PRODUCTS, StatusReason.NO_PRODUCTS.messageTemplate));
                }
            } else {
                Integer totalProducts = latest != null ? latest.getTotalProducts() : null;
                if (latest != null
                        && (totalProducts == null || totalProducts == 0)
                        && (latestInStock == null || latestInStock == 0)
                        && latest.getCompletionPercent() != null && latest.getCompletionPercent() >= 100) {
                    issues.add(new SiteIssue(StatusReason.EMPTY_RESULT, StatusReason.EMPTY_RESULT.messageTemplate));
                } else if (latestInStock != null && latestInStock == 0) {
                    issues.add(new SiteIssue(StatusReason.STOCK_ZERO, StatusReason.STOCK_ZERO.messageTemplate));
                } else if (latestInStock != null) {
                    baselineInStock = computeMedian(pickBestPerDayList(baselineStats), s -> s.getInStock() != null ? (double) s.getInStock() : null);
                    if (baselineInStock != null && baselineInStock > 0) {
                        double drop = (baselineInStock - latestInStock) / baselineInStock * 100;
                        inStockDelta = latestInStock - (int) Math.round(baselineInStock);
                        inStockDeltaPercent = (int) Math.round((latestInStock - baselineInStock) / baselineInStock * 100);
                        if ("eapteka.ru".equals(debugSite) && "4400".equals(cityId)) {
                            log.info("eapteka DEBUG: latest.inStock={}, baselineMaxMedian={}, dropPercent={}, threshold={}",
                                    latestInStock, baselineInStock, String.format("%.1f", drop), dropThreshold);
                            log.info("eapteka baseline stats count: {}", baselineStats.size());
                            baselineStats.forEach(s -> log.info("  baseline: server={} inStock={} date={}",
                                    s.getServerName(), s.getInStock(), s.getParsingDate()));
                        }
                        if (drop >= dropThreshold) {
                            issues.add(new SiteIssue(StatusReason.STOCK_DROP,
                                    String.format("В наличии упало на %.0f%% (порог %d%%)", drop, dropThreshold)));
                        }
                    }
                }
            }
        } else {
            // Завершённых нет — анализируем inProgress (STALLED возможен)
            latest = null;
            latestInStock = null;

            // Зависание определяем по САМОМУ СВЕЖЕМУ updatedTime среди всех inProgress записей.
            // Итерация по отдельным записям ошибочна: в списке могут быть старые записи
            // предыдущего дня (из parseInProgressPage с dateFrom-1), которые ложно дают STALLED.
            Optional<ZonedDateTime> latestUpdate = inProgress.stream()
                    .map(ZoomosParsingStats::getUpdatedTime)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder());
            if (latestUpdate.isPresent()
                    && Duration.between(latestUpdate.get(), now).toMinutes() >= stallMinutes) {
                stalledFound = true;
                issues.add(new SiteIssue(StatusReason.STALLED,
                        "Выкачка зависла (нет обновлений " + stallMinutes + " мин)"));
            }

            if (!stalledFound) for (ZoomosParsingStats ip : inProgress) {
                if (deadline != null && deadline.isBefore(now)) {
                    issues.add(new SiteIssue(StatusReason.DEADLINE_MISSED, StatusReason.DEADLINE_MISSED.messageTemplate));
                } else if (ip.getStartTime() != null && ip.getUpdatedTime() != null
                        && ip.getCompletionPercent() != null && ip.getCompletionPercent() > 0) {
                    long elapsed = Duration.between(ip.getStartTime(), ip.getUpdatedTime()).toMinutes();
                    if (elapsed > 0) {
                        long total = (long) (elapsed / (ip.getCompletionPercent() / 100.0));
                        ZonedDateTime est = ip.getStartTime().plusMinutes(total);
                        boolean reliable = ip.getCompletionPercent() >= 10;
                        estFinish  = est;
                        estReliable = reliable;
                        if (deadline != null && est.isAfter(deadline)) {
                            issues.add(new SiteIssue(StatusReason.IN_PROGRESS_RISK,
                                    "Идёт, не успеет к дедлайну " + deadline.format(TIME_FMT)));
                        } else {
                            String note = reliable ? "" : " (прогноз неточный, " + ip.getCompletionPercent() + "%)";
                            issues.add(new SiteIssue(StatusReason.IN_PROGRESS_OK,
                                    "Идёт, ожидаемое завершение " + est.format(TIME_FMT) + note));
                        }
                    } else {
                        issues.add(new SiteIssue(StatusReason.IN_PROGRESS_OK, "Идёт, прогноз недоступен"));
                    }
                } else {
                    issues.add(new SiteIssue(StatusReason.IN_PROGRESS_OK, "Идёт, прогноз недоступен"));
                }
            }
        }

        boolean anyFinished       = !finished.isEmpty();
        boolean anyInProgressLeft = !inProgress.isEmpty() && !stalledFound;
        ZoomosResultLevel status = issues.stream()
                .map(i -> i.reason().level)
                .min(Comparator.comparingInt(l -> l.priority))
                .orElse(anyFinished ? ZoomosResultLevel.OK
                        : (anyInProgressLeft ? ZoomosResultLevel.IN_PROGRESS : ZoomosResultLevel.OK));

        return new CityResult(cityId, cityName, status, latestInStock, sortedIssues(issues), estFinish, estReliable, stalledFound, baselineInStock, inStockDelta, inStockDeltaPercent, null, null, null, null,
                lastKnownDate, lastKnownInStock, lastKnownCompletionPercent, lastKnownIsStalled, lastKnownUpdatedTime);
    }

    // =========================================================================
    // Утилиты
    // =========================================================================

    private ZoomosResultLevel determineStatus(List<SiteIssue> issues, boolean hasFinished) {
        return issues.stream()
                .map(SiteIssue::level)
                .min(Comparator.comparingInt(l -> l.priority))
                .orElse(hasFinished ? ZoomosResultLevel.OK : ZoomosResultLevel.IN_PROGRESS);
    }

    private String buildCategoryMissingMsg(String parserInclude, List<ZoomosParsingStats> stats) {
        String found = stats.stream()
                .map(ZoomosParsingStats::getParserDescription)
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .distinct().limit(3)
                .collect(Collectors.joining(", "));
        return "Не найдено: [" + parserInclude + "]"
                + (found.isBlank() ? "" : ". В парсере: " + found);
    }

    private List<ZoomosParsingStats> applyParserFilter(List<ZoomosParsingStats> stats, SiteConfig config) {
        if (config.parserInclude() == null || config.parserInclude().isBlank()) return stats;
        return stats.stream()
                .filter(s -> matchesParserInclude(s.getParserDescription(), config.parserInclude(), config.parserIncludeMode()))
                .collect(Collectors.toList());
    }

    private boolean matchesParserInclude(String desc, String include, String mode) {
        if (include == null || include.isBlank()) return true;
        if (desc == null) return false;
        String descLow = desc.toLowerCase();
        List<String> patterns = Arrays.stream(include.split(";"))
                .map(String::trim).filter(p -> !p.isEmpty())
                .map(String::toLowerCase).collect(Collectors.toList());
        return "AND".equalsIgnoreCase(mode)
                ? patterns.stream().allMatch(descLow::contains)
                : patterns.stream().anyMatch(descLow::contains);
    }

    private Set<String> parseExpectedCities(String cityIds) {
        if (cityIds == null || cityIds.isBlank()) return Set.of();
        return Arrays.stream(cityIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private Double computeMedian(List<ZoomosParsingStats> stats,
                                  java.util.function.Function<ZoomosParsingStats, Double> extractor) {
        List<Double> values = stats.stream()
                .map(extractor).filter(Objects::nonNull)
                .sorted().collect(Collectors.toList());
        if (values.isEmpty()) return null;
        int n = values.size();
        return n % 2 == 0 ? (values.get(n / 2 - 1) + values.get(n / 2)) / 2 : values.get(n / 2);
    }

    private ZoomosParsingStats pickBestPerDay(List<ZoomosParsingStats> stats) {
        return stats.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsFinished())
                        || (s.getCompletionPercent() != null && s.getCompletionPercent() >= 100))
                .collect(Collectors.groupingBy(
                        ZoomosParsingStats::getParsingDate,
                        Collectors.maxBy(Comparator.comparingInt(
                                s -> s.getTotalProducts() != null ? s.getTotalProducts() : 0))))
                .values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparingInt(s -> s.getTotalProducts() != null ? s.getTotalProducts() : 0))
                .orElse(null);
    }

    private List<ZoomosParsingStats> pickBestPerDayList(List<ZoomosParsingStats> stats) {
        return stats.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsFinished())
                        || (s.getCompletionPercent() != null && s.getCompletionPercent() >= 100))
                .collect(Collectors.groupingBy(
                        ZoomosParsingStats::getParsingDate,
                        Collectors.maxBy(Comparator.comparingInt(
                                s -> s.getTotalProducts() != null ? s.getTotalProducts() : 0))))
                .values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private int countConsecutiveDrop(List<Integer> values) {
        if (values.size() < 2) return 0;
        int count = 0;
        for (int i = values.size() - 1; i > 0; i--) {
            if (values.get(i) < values.get(i - 1)) count++;
            else break;
        }
        return count;
    }

    private boolean isConsecutivelyIncreasing(List<Double> values) {
        int n = values.size();
        return n >= 3 && values.get(n - 1) > values.get(n - 2) && values.get(n - 2) > values.get(n - 3);
    }

    private List<ZoomosParsingStats> findStatsBySiteName(List<ZoomosParsingStats> stats, String siteName) {
        return stats.stream().filter(s -> siteName.equals(s.getSiteName())).collect(Collectors.toList());
    }

    private List<ZoomosParsingStats> findStatsByCityId(List<ZoomosParsingStats> stats, String siteName, String cityId) {
        return stats.stream()
                .filter(s -> siteName.equals(s.getSiteName())
                        && cityId.equals(ZoomosCheckService.extractCityId(s.getCityName())))
                .collect(Collectors.toList());
    }

    private List<SparklinePoint> computeHistory(List<ZoomosParsingStats> baselineStats,
                                                 Function<ZoomosParsingStats, Integer> extractor) {
        List<ZoomosParsingStats> best = pickBestPerDayList(baselineStats);
        if (best.isEmpty()) return List.of();
        java.util.TreeMap<java.time.LocalDate, List<Integer>> byDate = new java.util.TreeMap<>();
        for (ZoomosParsingStats s : best) {
            Integer val = extractor.apply(s);
            if (s.getParsingDate() != null && val != null) {
                byDate.computeIfAbsent(s.getParsingDate(), k -> new ArrayList<>()).add(val);
            }
        }
        List<SparklinePoint> all = byDate.entrySet().stream()
                .map(e -> {
                    List<Integer> vals = e.getValue().stream().sorted().collect(Collectors.toList());
                    int n = vals.size();
                    int med = n % 2 == 0 ? (vals.get(n / 2 - 1) + vals.get(n / 2)) / 2 : vals.get(n / 2);
                    return new SparklinePoint(e.getKey(), med);
                })
                .collect(Collectors.toList());
        int start = Math.max(0, all.size() - 7);
        return all.subList(start, all.size());
    }

    private List<SparklinePoint> computeInStockHistory(List<ZoomosParsingStats> baselineStats) {
        return computeHistory(baselineStats, ZoomosParsingStats::getInStock);
    }

    private List<SparklinePoint> computeErrorHistory(List<ZoomosParsingStats> baselineStats) {
        return computeHistory(baselineStats, ZoomosParsingStats::getErrorCount);
    }

    private TreeMap<java.time.LocalDate, Double> medianSpeedPerDay(List<ZoomosParsingStats> stats) {
        return stats.stream()
                .filter(s -> s.getParsingDate() != null)
                .collect(Collectors.groupingBy(
                    ZoomosParsingStats::getParsingDate,
                    TreeMap::new,
                    Collectors.collectingAndThen(Collectors.toList(), list -> computeMedian(list, s ->
                        (s.getTotalProducts() != null && s.getTotalProducts() > 100 && s.getParsingDurationMinutes() != null)
                                ? s.getParsingDurationMinutes().doubleValue() / s.getTotalProducts() * 1000 : null))
                ));
    }

    private List<SparklinePoint> computeSpeedHistory(List<ZoomosParsingStats> baselineStats) {
        List<ZoomosParsingStats> finished = baselineStats.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsFinished())
                        || (s.getCompletionPercent() != null && s.getCompletionPercent() >= 100))
                .collect(Collectors.toList());
        List<SparklinePoint> all = medianSpeedPerDay(finished).entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> new SparklinePoint(e.getKey(), (int) Math.round(e.getValue())))
                .collect(Collectors.toList());
        int start = Math.max(0, all.size() - 7);
        return all.subList(start, all.size());
    }

    private Map<String, SiteConfig> buildProfileConfigs(Long profileId, Map<String, ZoomosKnownSite> knownSiteMap) {
        ZoomosCheckProfile profile = profileRepository.findByIdWithSites(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Профиль не найден: " + profileId));
        return profile.getSites().stream()
                .filter(ZoomosProfileSite::isActive)
                .collect(Collectors.toMap(
                        ZoomosProfileSite::getSiteName,
                        s -> {
                            ZoomosKnownSite ks = knownSiteMap.get(s.getSiteName());
                            return new SiteConfig(s.getSiteName(), s.getCityIds(), s.getAccountFilter(),
                                    s.getParserInclude(), s.getParserIncludeMode(), s.getParserExclude(),
                                    ks != null ? ks.getMasterCityId() : null);
                        },
                        (a, b) -> a));
    }

    private Map<String, SiteConfig> buildLegacyConfigs(Long shopId, Map<String, ZoomosKnownSite> knownSiteMap) {
        return cityIdRepository.findByShopIdOrderBySiteName(shopId).stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .collect(Collectors.toMap(
                        ZoomosCityId::getSiteName,
                        c -> {
                            ZoomosKnownSite ks = knownSiteMap.get(c.getSiteName());
                            // ZoomosKnownSite.masterCityId — авторитетный источник (обновляется через /sites/{id}/master-city)
                            // ZoomosCityId.masterCityId — устаревший, обновляется только через /city-ids/{id}/master-city
                            String masterCityId = (ks != null && ks.getMasterCityId() != null)
                                    ? ks.getMasterCityId() : c.getMasterCityId();
                            return new SiteConfig(c.getSiteName(), c.getCityIds(), null,
                                    c.getParserInclude(), c.getParserIncludeMode(), c.getParserExclude(),
                                    masterCityId);
                        },
                        (a, b) -> a));
    }

    private static final Map<StatusReason, Integer> REASON_ORDER;
    static {
        REASON_ORDER = new java.util.EnumMap<>(StatusReason.class);
        // CRITICAL
        REASON_ORDER.put(StatusReason.NOT_FOUND,        1);
        REASON_ORDER.put(StatusReason.STALLED,          2);
        REASON_ORDER.put(StatusReason.DEADLINE_MISSED,  3);
        REASON_ORDER.put(StatusReason.IN_PROGRESS_RISK, 3);
        REASON_ORDER.put(StatusReason.STOCK_ZERO,       4);
        REASON_ORDER.put(StatusReason.NO_PRODUCTS,      4);
        REASON_ORDER.put(StatusReason.STOCK_DROP,       5);
        REASON_ORDER.put(StatusReason.CITIES_MISSING,   6);
        REASON_ORDER.put(StatusReason.ACCOUNT_MISSING,  7);
        REASON_ORDER.put(StatusReason.CATEGORY_MISSING, 8);
        // WARNING
        REASON_ORDER.put(StatusReason.ERROR_GROWTH,     1);
        REASON_ORDER.put(StatusReason.STOCK_TREND_DOWN, 2);
        REASON_ORDER.put(StatusReason.EMPTY_RESULT,     3);
        // TREND
        REASON_ORDER.put(StatusReason.SPEED_SPIKE,      1);
        REASON_ORDER.put(StatusReason.SPEED_TREND,      2);
        // IN_PROGRESS
        REASON_ORDER.put(StatusReason.IN_PROGRESS_OK,   1);
    }

    private static List<SiteIssue> sortedIssues(List<SiteIssue> issues) {
        return issues.stream()
                .sorted(Comparator
                        .comparingInt((SiteIssue i) -> i.reason().level.priority)
                        .thenComparingInt(i -> REASON_ORDER.getOrDefault(i.reason(), 99)))
                .collect(Collectors.toList());
    }

    private record SiteConfig(
            String siteName,
            String cityIds,
            String accountFilter,
            String parserInclude,
            String parserIncludeMode,
            String parserExclude,
            String masterCityId
    ) {}
}
