package com.java.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.config.ZoomosConfig;
import com.java.model.entity.ZoomosCityId;
import com.java.model.entity.ZoomosKnownSite;
import com.java.model.entity.ZoomosSession;
import com.java.model.entity.ZoomosShop;
import com.java.repository.ZoomosCityIdRepository;
import com.java.repository.ZoomosKnownSiteRepository;
import com.java.repository.ZoomosSessionRepository;
import com.java.repository.ZoomosShopRepository;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SameSiteAttribute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZoomosParserService {

    private final ZoomosConfig config;
    private final ZoomosShopRepository shopRepository;
    private final ZoomosCityIdRepository cityIdRepository;
    private final ZoomosSessionRepository sessionRepository;
    private final ZoomosKnownSiteRepository knownSiteRepository;
    private final ObjectMapper objectMapper;

    /**
     * Добавить новый магазин в БД
     */
    @Transactional
    public ZoomosShop addShop(String shopName) {
        String name = shopName.trim().toLowerCase();
        if (shopRepository.existsByShopName(name)) {
            throw new IllegalArgumentException("Магазин '" + name + "' уже добавлен");
        }
        ZoomosShop shop = ZoomosShop.builder().shopName(name).build();
        return shopRepository.save(shop);
    }

    /**
     * Удалить магазин
     */
    @Transactional
    public void deleteShop(Long shopId) {
        shopRepository.deleteById(shopId);
    }

    /**
     * Получить все магазины с их данными о городах
     */
    @Transactional(readOnly = true)
    public List<ZoomosShop> getAllShops() {
        return shopRepository.findAll();
    }

    /**
     * Получить ID городов для магазина
     */
    @Transactional(readOnly = true)
    public List<ZoomosCityId> getCityIds(Long shopId) {
        return cityIdRepository.findByShopIdOrderBySiteName(shopId);
    }

    /**
     * Переключить активность строки city_ids
     */
    @Transactional
    public ZoomosCityId toggleCityId(Long id) {
        ZoomosCityId entry = cityIdRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Запись не найдена: " + id));
        entry.setIsActive(!Boolean.TRUE.equals(entry.getIsActive()));
        return cityIdRepository.save(entry);
    }

    /**
     * Обновить city_ids для строки (ручное редактирование)
     */
    @Transactional
    public ZoomosCityId updateCityIds(Long id, String cityIds) {
        ZoomosCityId entry = cityIdRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Запись не найдена: " + id));
        entry.setCityIds(cityIds);
        return cityIdRepository.save(entry);
    }

    /**
     * Обновить тип проверки для строки (API / ITEM)
     */
    @Transactional
    public ZoomosCityId updateCheckType(Long id, String checkType) {
        if (!"API".equals(checkType) && !"ITEM".equals(checkType)) {
            throw new IllegalArgumentException("Недопустимый тип: " + checkType);
        }
        ZoomosCityId entry = cityIdRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Запись не найдена: " + id));
        entry.setCheckType(checkType);
        cityIdRepository.save(entry);
        // Каскад: обновляем справочник
        knownSiteRepository.findBySiteName(entry.getSiteName()).ifPresent(known -> {
            known.setCheckType(checkType);
            knownSiteRepository.save(known);
        });
        return entry;
    }

    /**
     * Удалить отдельную запись ZoomosCityId
     */
    @Transactional
    public void deleteCityId(Long id) {
        cityIdRepository.deleteById(id);
    }

    // =========================================================================
    // Синхронизация настроек (только city_ids для существующих сайтов)
    // =========================================================================

    /**
     * Основной метод: парсинг страницы настроек магазина через Playwright.
     * ТОЛЬКО обновляет city_ids для уже существующих сайтов.
     * Не добавляет новые сайты и не удаляет старые.
     */
    @Transactional
    public String syncShopSettings(String shopName) {
        ZoomosShop shop = shopRepository.findByShopName(shopName.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден: " + shopName));

        log.info("Начало синхронизации настроек для: {}", shopName);

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true);
            Browser browser = playwright.chromium().launch(launchOptions);
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1920, 1080));

            context.setDefaultTimeout(config.getTimeoutSeconds() * 1000L);

            if (!loadSession(context)) {
                login(context);
            }

            Page page = context.newPage();
            String settingsUrl = config.getBaseUrl() + "/shop/" + shopName + "/settings?upd=" + System.currentTimeMillis();
            page.navigate(settingsUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            if (page.url().contains("/login")) {
                log.info("Сессия устарела, повторная авторизация...");
                login(context);
                page.navigate(settingsUrl);
                page.waitForLoadState(LoadState.NETWORKIDLE);
            }

            saveSession(context);

            int count = parseCityIds(page, shop);

            shop.setLastSyncedAt(ZonedDateTime.now());
            shopRepository.save(shop);

            log.info("Синхронизация завершена: {} записей для {}", count, shopName);
            return "Обновлено city_ids для " + count + " сайтов";

        } catch (Exception e) {
            log.error("Ошибка синхронизации {}: {}", shopName, e.getMessage(), e);
            throw new RuntimeException("Ошибка синхронизации: " + e.getMessage(), e);
        }
    }

    /**
     * Preview: парсит страницу настроек и возвращает список изменений city_ids
     * без сохранения в БД.
     * Возвращает {updates: [{entryId, site, oldCityIds, newCityIds}]}
     */
    public Map<String, Object> previewSyncSettings(String shopName) {
        ZoomosShop shop = shopRepository.findByShopName(shopName.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден: " + shopName));

        log.info("Preview синхронизации настроек для: {}", shopName);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
            context.setDefaultTimeout(config.getTimeoutSeconds() * 1000L);

            if (!loadSession(context)) login(context);

            Page page = context.newPage();
            String settingsUrl = config.getBaseUrl() + "/shop/" + shopName + "/settings?upd=" + System.currentTimeMillis();
            page.navigate(settingsUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            if (page.url().contains("/login")) {
                login(context);
                page.navigate(settingsUrl);
                page.waitForLoadState(LoadState.NETWORKIDLE);
            }
            saveSession(context);

            Map<String, String> parsedMap = parseSiteIdMapFromPage(page);
            page.close();
            browser.close();

            Map<String, ZoomosCityId> existingMap = cityIdRepository.findByShopIdOrderBySiteName(shop.getId())
                    .stream().collect(Collectors.toMap(ZoomosCityId::getSiteName, c -> c));

            List<Map<String, Object>> updates = new ArrayList<>();
            for (Map.Entry<String, String> e : parsedMap.entrySet()) {
                String sn = e.getKey();
                String newCityIds = e.getValue();
                ZoomosCityId existing = existingMap.get(sn);
                if (existing == null) continue; // не добавляем новые
                String oldCityIds = existing.getCityIds() != null ? existing.getCityIds() : "";
                if (!oldCityIds.equals(newCityIds)) {
                    Map<String, Object> upd = new LinkedHashMap<>();
                    upd.put("entryId", existing.getId());
                    upd.put("site", sn);
                    upd.put("oldCityIds", oldCityIds);
                    upd.put("newCityIds", newCityIds);
                    updates.add(upd);
                }
            }

            log.info("Preview настроек: найдено {} изменений для {}", updates.size(), shopName);
            return Map.of("updates", updates);

        } catch (Exception e) {
            log.error("Ошибка preview настроек {}: {}", shopName, e.getMessage(), e);
            throw new RuntimeException("Ошибка: " + e.getMessage(), e);
        }
    }

    /**
     * Apply: применяет изменения city_ids (данные уже получены от preview, без Playwright).
     * @param entryIdToCityIds Map<entryId, newCityIds>
     */
    @Transactional
    public String applySyncSettings(String shopName, Map<Long, String> entryIdToCityIds) {
        ZoomosShop shop = shopRepository.findByShopName(shopName.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден: " + shopName));

        int updated = 0;
        for (Map.Entry<Long, String> e : entryIdToCityIds.entrySet()) {
            Optional<ZoomosCityId> opt = cityIdRepository.findById(e.getKey());
            if (opt.isPresent()) {
                ZoomosCityId entry = opt.get();
                entry.setCityIds(e.getValue());
                cityIdRepository.save(entry);
                updated++;
            }
        }

        shop.setLastSyncedAt(ZonedDateTime.now());
        shopRepository.save(shop);
        log.info("Apply настроек: обновлено {} записей для {}", updated, shopName);
        return "Обновлено city_ids для " + updated + " сайтов";
    }

    // =========================================================================
    // Синхронизация из матчинга (полная синхронизация списка сайтов)
    // =========================================================================

    /**
     * Preview: парсит страницу матчинга и возвращает diff (без сохранения).
     * Возвращает {toAdd: [...], toDelete: [...], existing: [...], total: N}
     */
    public Map<String, Object> previewSyncFromMatching(String shopName) {
        ZoomosShop shop = shopRepository.findByShopName(shopName.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден: " + shopName));

        log.info("Preview синхронизации из матчинга для: {}", shopName);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
            context.setDefaultTimeout(config.getTimeoutSeconds() * 1000L);

            if (!loadSession(context)) login(context);

            Page page = context.newPage();
            String url = config.getBaseUrl() + "/shop/" + shopName + "/sites-items-mapping?upd=" + System.currentTimeMillis();
            page.navigate(url);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            if (page.url().contains("/login")) {
                login(context);
                page.navigate(url);
                page.waitForLoadState(LoadState.NETWORKIDLE);
            }
            saveSession(context);

            @SuppressWarnings("unchecked")
            List<String> siteNames = (List<String>) page.evaluate(
                    "() => Array.from(document.querySelectorAll('select[name=\"site\"] option'))" +
                    ".filter(o => o.value).map(o => o.value)");

            page.close();
            browser.close();

            if (siteNames == null) siteNames = List.of();

            Map<String, ZoomosCityId> existingMap = cityIdRepository.findByShopIdOrderBySiteName(shop.getId())
                    .stream().collect(Collectors.toMap(ZoomosCityId::getSiteName, c -> c));

            Set<String> siteSet = new HashSet<>(siteNames);
            List<String> toAdd = siteNames.stream()
                    .filter(s -> !existingMap.containsKey(s))
                    .collect(Collectors.toList());
            List<String> toDelete = existingMap.keySet().stream()
                    .filter(s -> !siteSet.contains(s))
                    .sorted()
                    .collect(Collectors.toList());
            List<String> existing = siteNames.stream()
                    .filter(existingMap::containsKey)
                    .collect(Collectors.toList());

            log.info("Preview матчинга: toAdd={}, toDelete={}, existing={} для {}",
                    toAdd.size(), toDelete.size(), existing.size(), shopName);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("toAdd", toAdd);
            result.put("toDelete", toDelete);
            result.put("existing", existing);
            result.put("total", siteNames.size());
            return result;

        } catch (Exception e) {
            log.error("Ошибка preview из матчинга {}: {}", shopName, e.getMessage(), e);
            throw new RuntimeException("Ошибка: " + e.getMessage(), e);
        }
    }

    /**
     * Apply: применяет diff из матчинга (данные уже получены от preview, без Playwright).
     * Добавляет toAdd и удаляет toDelete из zoomos_city_ids.
     */
    @Transactional
    public String applySyncFromMatching(String shopName, List<String> toAdd, List<String> toDelete) {
        ZoomosShop shop = shopRepository.findByShopName(shopName.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден: " + shopName));

        int added = 0;
        for (String siteName : toAdd) {
            String checkType = knownSiteRepository.findBySiteName(siteName)
                    .map(ZoomosKnownSite::getCheckType).orElse("ITEM");
            cityIdRepository.save(ZoomosCityId.builder()
                    .shop(shop).siteName(siteName).checkType(checkType).build());
            added++;
        }

        int deleted = 0;
        for (String siteName : toDelete) {
            cityIdRepository.findByShopIdAndSiteName(shop.getId(), siteName)
                    .ifPresent(cityIdRepository::delete);
            deleted++;
        }

        shop.setLastSyncedAt(ZonedDateTime.now());
        shopRepository.save(shop);
        log.info("Apply матчинга: добавлено={}, удалено={} для {}", added, deleted, shopName);
        return String.format("Добавлено: %d, удалено: %d", added, deleted);
    }

    /**
     * Устаревший метод: загружает список сайтов со страницы матчинга и добавляет новые.
     * Оставлен для обратной совместимости. Рекомендуется использовать preview/apply.
     */
    @Transactional
    public String syncFromMatchingPage(String shopName) {
        ZoomosShop shop = shopRepository.findByShopName(shopName.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден: " + shopName));

        log.info("Синхронизация сайтов из матчинга для: {}", shopName);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions().setViewportSize(1920, 1080));
            context.setDefaultTimeout(config.getTimeoutSeconds() * 1000L);

            if (!loadSession(context)) {
                login(context);
            }

            Page page = context.newPage();
            String url = config.getBaseUrl() + "/shop/" + shopName + "/sites-items-mapping"
                    + "?upd=" + System.currentTimeMillis();
            page.navigate(url);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            if (page.url().contains("/login")) {
                login(context);
                page.navigate(url);
                page.waitForLoadState(LoadState.NETWORKIDLE);
            }
            saveSession(context);

            @SuppressWarnings("unchecked")
            List<String> siteNames = (List<String>) page.evaluate(
                    "() => Array.from(document.querySelectorAll('select[name=\"site\"] option'))" +
                    ".filter(o => o.value).map(o => o.value)");

            page.close();
            browser.close();

            if (siteNames == null || siteNames.isEmpty()) {
                return "Сайты на странице матчинга не найдены";
            }

            Map<String, ZoomosCityId> existing = cityIdRepository.findByShopIdOrderBySiteName(shop.getId())
                    .stream().collect(Collectors.toMap(ZoomosCityId::getSiteName, c -> c));

            int added = 0;
            for (String siteName : siteNames) {
                if (!existing.containsKey(siteName)) {
                    String checkType = knownSiteRepository.findBySiteName(siteName)
                            .map(ZoomosKnownSite::getCheckType).orElse("ITEM");
                    cityIdRepository.save(ZoomosCityId.builder()
                            .shop(shop).siteName(siteName).checkType(checkType).build());
                    added++;
                }
            }

            log.info("Из матчинга: найдено={}, добавлено={} для {}", siteNames.size(), added, shopName);
            return "Добавлено " + added + " новых сайтов из " + siteNames.size() + " найденных на странице матчинга";

        } catch (Exception e) {
            log.error("Ошибка синхронизации из матчинга {}: {}", shopName, e.getMessage(), e);
            throw new RuntimeException("Ошибка: " + e.getMessage(), e);
        }
    }

    /**
     * Авторизация через форму логина.
     */
    private void login(BrowserContext context) {
        Page page = context.newPage();
        try {
            log.info("Авторизация на {}", config.getBaseUrl());
            page.navigate(config.getBaseUrl() + "/login");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            page.fill("input[name='j_username']", config.getUsername());
            page.fill("input[name='j_password']", config.getPassword());
            page.click("input[type='submit']");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            if (page.url().contains("/login")) {
                throw new RuntimeException("Авторизация не удалась — проверьте логин и пароль в настройках zoomos.*");
            }
            log.info("Авторизация успешна, URL: {}", page.url());
        } finally {
            page.close();
        }
    }

    /**
     * Загрузить куки из БД в BrowserContext.
     */
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
                    if (raw.get("path") != null)   c.setPath((String) raw.get("path"));
                    if (raw.get("expires") != null) c.setExpires(((Number) raw.get("expires")).doubleValue());
                    if (raw.get("httpOnly") != null) c.setHttpOnly((Boolean) raw.get("httpOnly"));
                    if (raw.get("secure") != null)   c.setSecure((Boolean) raw.get("secure"));
                    if (raw.get("sameSite") != null) {
                        try { c.setSameSite(SameSiteAttribute.valueOf((String) raw.get("sameSite"))); }
                        catch (IllegalArgumentException ignored) {}
                    }
                    cookies.add(c);
                }
                context.addCookies(cookies);
                log.debug("Куки загружены из БД ({} шт.)", cookies.size());
                return true;
            } catch (Exception e) {
                log.warn("Не удалось загрузить куки: {}", e.getMessage());
                return false;
            }
        }).orElse(false);
    }

    /**
     * Сохранить куки из BrowserContext в БД
     */
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
            log.debug("Куки сохранены в БД ({} шт.)", cookies.size());
        } catch (Exception e) {
            log.warn("Не удалось сохранить куки: {}", e.getMessage());
        }
    }

    /**
     * Парсит таблицу ID городов со страницы настроек.
     * Возвращает Map<siteName, cityIds>.
     */
    private Map<String, String> parseSiteIdMapFromPage(Page page) {
        List<ElementHandle> rows = page.querySelectorAll("tr");
        Map<String, String> result = new LinkedHashMap<>();

        for (ElementHandle row : rows) {
            try {
                ElementHandle labelCell = row.querySelector("td:first-child");
                if (labelCell == null) continue;

                String labelText = labelCell.innerText().trim();
                if (!labelText.contains("ID городов")) continue;

                String siteName = labelText.replace("ID городов", "").replace(":", "").trim();
                if (siteName.isEmpty() || siteName.contains(" ")) continue;

                ElementHandle valueCell = row.querySelector("td:nth-child(2)");
                if (valueCell == null) continue;

                ElementHandle input = valueCell.querySelector("input[type='text']");
                String cityIds = input != null ? input.getAttribute("value") : valueCell.innerText().trim();
                if (cityIds == null) cityIds = "";

                result.put(siteName, cityIds);
            } catch (Exception e) {
                log.warn("Ошибка парсинга строки таблицы: {}", e.getMessage());
            }
        }

        return result;
    }

    /**
     * Обновляет city_ids только для уже существующих записей.
     * Не добавляет новые сайты и не удаляет старые.
     */
    private int parseCityIds(Page page, ZoomosShop shop) {
        Map<String, String> parsedMap = parseSiteIdMapFromPage(page);

        if (parsedMap.isEmpty()) {
            log.warn("Таблица ID городов не найдена на странице. URL: {}", page.url());
            return 0;
        }

        Map<String, ZoomosCityId> existingMap = cityIdRepository.findByShopIdOrderBySiteName(shop.getId())
                .stream().collect(Collectors.toMap(ZoomosCityId::getSiteName, c -> c));

        List<ZoomosCityId> toUpdate = new ArrayList<>();
        for (Map.Entry<String, String> e : parsedMap.entrySet()) {
            ZoomosCityId existing = existingMap.get(e.getKey());
            if (existing != null) {
                existing.setCityIds(e.getValue());
                toUpdate.add(existing);
            }
            // Новые сайты добавляются только через "Из матчинга" (preview/apply)
        }

        cityIdRepository.saveAll(toUpdate);
        return toUpdate.size();
    }
}
