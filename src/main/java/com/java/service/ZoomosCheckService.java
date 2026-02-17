package com.java.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.config.ZoomosConfig;
import com.java.model.entity.*;
import com.java.repository.*;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SameSiteAttribute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
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
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    private static final DateTimeFormatter DATE_PARAM_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_PARSE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yy H:mm");

    /**
     * Запуск проверки выкачки по всем активным сайтам клиента.
     */
    @Transactional
    public ZoomosCheckRun runCheck(Long shopId, LocalDate dateFrom, LocalDate dateTo,
                                    String timeFrom, String timeTo,
                                    int dropThreshold, int errorGrowthThreshold,
                                    String operationId) {
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
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime rangeStart = dateFrom.atTime(tFrom).atZone(zone);
        ZonedDateTime rangeEnd   = dateTo.atTime(tTo).atZone(zone);
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
                .build();
        run = checkRunRepository.save(run);

        // Группируем по типу проверки
        Map<String, List<ZoomosCityId>> byType = allCityIds.stream()
                .collect(Collectors.groupingBy(c -> c.getCheckType() != null ? c.getCheckType() : "API"));

        List<ZoomosParsingStats> allStats = new ArrayList<>();

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

                sendProgress(operationId, processed, total, "Проверяем " + siteName + " (API)...");

                List<ZoomosParsingStats> stats = parseApiPage(page, siteName, dateFrom, dateTo, cityIdEntries, run);
                if (hasTimeFilter) {
                    stats = filterByTime(stats, rangeStart, rangeEnd);
                }
                allStats.addAll(stats);

                processed += cityIdEntries.size();
                sendProgress(operationId, processed, total, siteName + " — " + stats.size() + " записей");
            }

            // --- Парсинг ITEM-сайтов (та же parsing-history + &shop=shopName) ---
            List<ZoomosCityId> itemSites = byType.getOrDefault("ITEM", Collections.emptyList());
            Map<String, List<ZoomosCityId>> itemSitesGrouped = itemSites.stream()
                    .collect(Collectors.groupingBy(ZoomosCityId::getSiteName));

            for (Map.Entry<String, List<ZoomosCityId>> entry : itemSitesGrouped.entrySet()) {
                String siteName = entry.getKey();
                List<ZoomosCityId> cityIdEntries = entry.getValue();

                sendProgress(operationId, processed, total,
                        "Проверяем " + siteName + " (ITEM, shop=" + shop.getShopName() + ")...");

                List<ZoomosParsingStats> stats = parseItemPage(page, siteName, shop.getShopName(),
                        dateFrom, dateTo, cityIdEntries, run);
                if (hasTimeFilter) {
                    stats = filterByTime(stats, rangeStart, rangeEnd);
                }
                allStats.addAll(stats);

                processed += cityIdEntries.size();
                sendProgress(operationId, processed, total, siteName + " — " + stats.size() + " записей");
            }

            // Дополнительный запрос для NOT_FOUND сайтов (без &onlyFinished=1)
            Set<String> sitesWithData = allStats.stream()
                    .map(ZoomosParsingStats::getSiteName).collect(Collectors.toSet());
            List<ZoomosCityId> notFoundSites = allCityIds.stream()
                    .filter(c -> !sitesWithData.contains(c.getSiteName()))
                    .collect(Collectors.toList());

            if (!notFoundSites.isEmpty()) {
                sendProgress(operationId, processed, total, "Проверка незавершённых выкачек...");
                Map<String, List<ZoomosCityId>> notFoundByType = notFoundSites.stream()
                        .collect(Collectors.groupingBy(c -> c.getCheckType() != null ? c.getCheckType() : "ITEM"));

                for (Map.Entry<String, List<ZoomosCityId>> entry : notFoundByType.entrySet()) {
                    for (ZoomosCityId cid : entry.getValue()) {
                        try {
                            List<ZoomosParsingStats> inProgressStats = parseInProgressPage(
                                    page, cid, shop.getShopName(), dateFrom, dateTo, run);
                            if (hasTimeFilter) {
                                inProgressStats = filterByTime(inProgressStats, rangeStart, rangeEnd);
                            }
                            allStats.addAll(inProgressStats);
                        } catch (Exception ex) {
                            log.warn("Ошибка проверки in-progress для {}: {}", cid.getSiteName(), ex.getMessage());
                        }
                    }
                }
            }

            page.close();
            browser.close();

        } catch (Exception e) {
            log.error("Ошибка проверки выкачки для {}: {}", shop.getShopName(), e.getMessage(), e);
            run.setStatus("FAILED");
            run.setCompletedAt(ZonedDateTime.now());
            checkRunRepository.save(run);
            sendProgress(operationId, 0, 0, "Ошибка: " + e.getMessage());
            throw new RuntimeException("Ошибка проверки: " + e.getMessage(), e);
        }

        // Сохраняем все результаты
        if (!allStats.isEmpty()) {
            parsingStatsRepository.saveAll(allStats);
        }

        // Подсчитываем итоги
        updateRunSummary(run, allStats, allCityIds);
        run.setStatus("COMPLETED");
        run.setCompletedAt(ZonedDateTime.now());
        run = checkRunRepository.save(run);

        sendProgress(operationId, run.getTotalSites(), run.getTotalSites(), "Проверка завершена");
        return run;
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
        Set<String> allowedCityIds = new HashSet<>();
        Set<String> allowedAddressIds = new HashSet<>();
        Map<String, ZoomosCityId> cityIdMap = new HashMap<>();
        for (ZoomosCityId entry : cityIdEntries) {
            if (entry.getCityIds() != null && !entry.getCityIds().isBlank()) {
                for (String cid : entry.getCityIds().split(",")) {
                    String trimmed = cid.trim();
                    if (!trimmed.isEmpty()) {
                        allowedCityIds.add(trimmed);
                        cityIdMap.put(trimmed, entry);
                    }
                }
            }
            if (entry.getAddressIds() != null && !entry.getAddressIds().isBlank()) {
                for (String aid : entry.getAddressIds().split(",")) {
                    String trimmed = aid.trim();
                    if (!trimmed.isEmpty()) allowedAddressIds.add(trimmed);
                }
            }
        }

        // Для API-сайтов берём только глобальные выкачки (поле "Клиент" пустое)
        return parseTable(page, run, siteName, "API", allowedCityIds, cityIdMap, allowedAddressIds).stream()
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

        Set<String> allowedCityIds = new HashSet<>();
        Set<String> allowedAddressIds = new HashSet<>();
        Map<String, ZoomosCityId> cityIdMap = new HashMap<>();
        for (ZoomosCityId entry : cityIdEntries) {
            if (entry.getCityIds() != null && !entry.getCityIds().isBlank()) {
                for (String cid : entry.getCityIds().split(",")) {
                    String trimmed = cid.trim();
                    if (!trimmed.isEmpty()) {
                        allowedCityIds.add(trimmed);
                        cityIdMap.put(trimmed, entry);
                    }
                }
            }
            if (entry.getAddressIds() != null && !entry.getAddressIds().isBlank()) {
                for (String aid : entry.getAddressIds().split(",")) {
                    String trimmed = aid.trim();
                    if (!trimmed.isEmpty()) allowedAddressIds.add(trimmed);
                }
            }
        }

        return parseTable(page, run, siteName, "ITEM", allowedCityIds, cityIdMap, allowedAddressIds);
    }

    // =========================================================================
    // Парсинг in-progress страницы для NOT_FOUND сайтов (без &onlyFinished=1)
    // =========================================================================

    private List<ZoomosParsingStats> parseInProgressPage(Page page, ZoomosCityId cid,
                                                          String shopName,
                                                          LocalDate dateFrom, LocalDate dateTo,
                                                          ZoomosCheckRun run) {
        String siteName = cid.getSiteName();
        String checkType = cid.getCheckType() != null ? cid.getCheckType() : "ITEM";

        String shopParam = "ITEM".equals(checkType) ? shopName : "-";
        String url = config.getBaseUrl() + "/shops-parser/" + siteName + "/parsing-history"
                + "?upd=" + System.currentTimeMillis()
                + "&dateFrom=" + dateFrom.format(DATE_PARAM_FORMAT)
                + "&dateTo=" + dateTo.format(DATE_PARAM_FORMAT)
                + "&launchDate=&shop=" + shopParam + "&site=&cityId=&address=&accountId=&server=";

        log.info("Парсинг in-progress страницы: {}", url);
        page.navigate(url);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Set<String> allowedCityIds = new HashSet<>();
        Set<String> allowedAddressIds = new HashSet<>();
        Map<String, ZoomosCityId> cityIdMap = new HashMap<>();
        if (cid.getCityIds() != null && !cid.getCityIds().isBlank()) {
            for (String id : cid.getCityIds().split(",")) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) {
                    allowedCityIds.add(trimmed);
                    cityIdMap.put(trimmed, cid);
                }
            }
        }
        if (cid.getAddressIds() != null && !cid.getAddressIds().isBlank()) {
            for (String aid : cid.getAddressIds().split(",")) {
                String trimmed = aid.trim();
                if (!trimmed.isEmpty()) allowedAddressIds.add(trimmed);
            }
        }

        List<ZoomosParsingStats> stats = parseTable(page, run, siteName, checkType, allowedCityIds, cityIdMap, allowedAddressIds);
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
                                                 Set<String> allowedAddressIds) {
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

        log.info("Найдены столбцы ({}): {}", colIndex.size(), colIndex.keySet());

        for (List<String> cells : rowsList) {
            try {
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
                        .parsingDate(parsingDate)
                        .checkType(checkType)
                        .isFinished(true)
                        .build();

                results.add(stat);

            } catch (Exception e) {
                log.warn("Ошибка парсинга строки таблицы: {}", e.getMessage());
            }
        }

        // Применяем комбинированный фильтр: address-покрытые города → только по addressId,
        // остальные города → по cityId
        if (!allowedCityIds.isEmpty() || !allowedAddressIds.isEmpty()) {
            // Находим cityId-ы, для которых в данных есть хотя бы одна строка с подходящим addressId.
            // Такие города считаются "покрытыми адресами" — их city-level строки исключаем.
            Set<String> addressCoveredCityIds = results.stream()
                    .filter(s -> s.getAddressId() != null && allowedAddressIds.contains(s.getAddressId()))
                    .map(s -> extractCityId(s.getCityName()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            results = results.stream().filter(s -> {
                String addrId = s.getAddressId();
                String cId    = extractCityId(s.getCityName());
                // Строка подходит по addressId → включаем всегда
                if (addrId != null && allowedAddressIds.contains(addrId)) return true;
                // Строка подходит по cityId И этот город не покрыт адресами → включаем
                if (cId != null && allowedCityIds.contains(cId) && !addressCoveredCityIds.contains(cId)) return true;
                return false;
            }).collect(Collectors.toList());
        }

        log.info("Распарсено {} записей (checkType={}) со страницы: {}", results.size(), checkType, page.url());
        return results;
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

    private String extractCityId(String cityStr) {
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

    private ZonedDateTime parseDateTime(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            // Формат: "15.02.26 21:40" или "15.02.26 06:28 (14.02.26 19:01)"
            // Берём первую часть до скобки
            String clean = str.contains("(") ? str.substring(0, str.indexOf("(")).trim() : str.trim();
            var local = java.time.LocalDateTime.parse(clean, DATETIME_PARSE_FORMAT);
            return local.atZone(ZoneId.systemDefault());
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
    // Итоги проверки
    // =========================================================================

    private void updateRunSummary(ZoomosCheckRun run, List<ZoomosParsingStats> stats,
                                   List<ZoomosCityId> allCityIds) {
        // Группируем данные по site+city+address (уникальная группа = одна "линия выкачки")
        // Если addressId задан — это отдельная группа внутри города
        Map<String, List<ZoomosParsingStats>> grouped = stats.stream()
                .collect(Collectors.groupingBy(s -> {
                    String key = s.getSiteName() + "|" + (s.getCityName() != null ? s.getCityName() : "");
                    if (s.getAddressId() != null && !s.getAddressId().isBlank()) {
                        key += "|" + s.getAddressId();
                    }
                    return key;
                }));

        // Определяем какие site+city были ожидаемы
        Set<String> expectedKeys = new HashSet<>();
        for (ZoomosCityId cid : allCityIds) {
            expectedKeys.add(cid.getSiteName() + "|" + (cid.getCityIds() != null ? cid.getCityIds() : ""));
        }

        // Считаем: для каждого site+city проверяем динамику внутри периода
        int notFound = 0;
        int ok = 0;
        int warning = 0;
        int error = 0;

        // Сначала — сайты без данных
        Set<String> sitesWithData = stats.stream()
                .map(ZoomosParsingStats::getSiteName)
                .collect(Collectors.toSet());

        for (ZoomosCityId cid : allCityIds) {
            if (!sitesWithData.contains(cid.getSiteName())) {
                notFound++;
            }
        }

        // Оцениваем каждую группу site+city
        for (Map.Entry<String, List<ZoomosParsingStats>> entry : grouped.entrySet()) {
            List<ZoomosParsingStats> group = entry.getValue();
            // Сортируем по startTime (хронологически)
            group.sort(Comparator.comparing(
                    s -> s.getStartTime() != null ? s.getStartTime() : ZonedDateTime.now(),
                    Comparator.naturalOrder()));

            String status = evaluateGroup(group, run.getDropThreshold(), run.getErrorGrowthThreshold());
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

    /**
     * Оценивает группу выкачек (один site+city за весь период).
     * Сортировка: ASC по startTime (от старых к новым).
     * Сравнивает последовательные выкачки: рост ошибок, падение "В наличии".
     *
     * Ключевая логика inStock:
     * - Если ВО ВСЕХ записях inStock == 0 или null — OK (особенность сайта, всегда нули)
     * - Если было > 0, а стало 0 — ERROR (явная проблема)
     * - Падение > dropThreshold% — WARNING, > dropThreshold*3% — ERROR
     */
    public String evaluateGroup(List<ZoomosParsingStats> sortedGroup, int dropThreshold, int errorGrowthThreshold) {
        // sortedGroup отсортирован ASC (от старых к новым)
        ZoomosParsingStats newest = sortedGroup.get(sortedGroup.size() - 1);

        // Одна запись — смотрим только на completionPercent
        if (sortedGroup.size() < 2) {
            if (newest.getCompletionPercent() != null && newest.getCompletionPercent() < 100) {
                return "WARNING";
            }
            return "OK";
        }

        // Сравниваем только ПОСЛЕДНЮЮ выкачку с ПРЕДПОСЛЕДНЕЙ — текущее состояние
        ZoomosParsingStats prev = sortedGroup.get(sortedGroup.size() - 2);

        // Проверяем, всегда ли inStock == 0/null во всей истории (особенность сайта)
        boolean alwaysZeroStock = sortedGroup.stream()
                .allMatch(s -> s.getInStock() == null || s.getInStock() == 0);

        boolean hasWarning = false;
        boolean hasError = false;

        double dropThresholdFraction = dropThreshold / 100.0;
        double errGrowthFraction = errorGrowthThreshold / 100.0;

        // --- ERROR только: падение "В наличии" (только если не всегда нули) ---
        if (!alwaysZeroStock) {
            Integer prevStock = prev.getInStock();
            Integer newStock = newest.getInStock();
            if (prevStock != null && prevStock > 0) {
                if (newStock != null && newStock == 0) {
                    hasError = true;
                } else if (newStock != null) {
                    double drop = (double)(prevStock - newStock) / prevStock;
                    if (drop > dropThresholdFraction) {
                        hasError = true;
                    }
                }
            }
        }

        // --- WARNING: рост ошибок ---
        int prevErrors = prev.getErrorCount() != null ? prev.getErrorCount() : 0;
        int newErrors = newest.getErrorCount() != null ? newest.getErrorCount() : 0;
        if (prevErrors > 0 && newErrors > prevErrors) {
            double growth = (double)(newErrors - prevErrors) / prevErrors;
            if (growth > errGrowthFraction) {
                hasWarning = true;
            }
        } else if (prevErrors == 0 && newErrors > 10) {
            hasWarning = true;
        }

        // --- WARNING: падение товаров ---
        Integer prevTotal = prev.getTotalProducts();
        Integer newTotal = newest.getTotalProducts();
        if (prevTotal != null && newTotal != null && prevTotal > 0) {
            double drop = (double)(prevTotal - newTotal) / prevTotal;
            if (drop > dropThresholdFraction) {
                hasWarning = true;
            }
        }

        if (hasError) return "ERROR";
        if (hasWarning) return "WARNING";
        return "OK";
    }

    // =========================================================================
    // WebSocket прогресс
    // =========================================================================

    private void sendProgress(String operationId, int current, int total, String message) {
        if (operationId == null) return;
        Map<String, Object> progress = Map.of(
                "current", current,
                "total", total,
                "message", message,
                "percent", total > 0 ? (current * 100 / total) : 0
        );
        messagingTemplate.convertAndSend("/topic/zoomos-check/" + operationId, progress);
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
}
