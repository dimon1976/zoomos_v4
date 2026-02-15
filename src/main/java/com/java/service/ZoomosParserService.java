package com.java.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.config.ZoomosConfig;
import com.java.model.entity.ZoomosCityId;
import com.java.model.entity.ZoomosSession;
import com.java.model.entity.ZoomosShop;
import com.java.repository.ZoomosCityIdRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ZoomosParserService {

    private final ZoomosConfig config;
    private final ZoomosShopRepository shopRepository;
    private final ZoomosCityIdRepository cityIdRepository;
    private final ZoomosSessionRepository sessionRepository;
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
     * Основной метод: парсинг страницы настроек магазина через Playwright
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

            // Загружаем сохранённые куки или авторизуемся
            if (!loadSession(context)) {
                login(context);
            }

            Page page = context.newPage();

            // Проверяем — может куки протухли (редирект на /login)
            String settingsUrl = config.getBaseUrl() + "/shop/" + shopName + "/settings?upd=" + System.currentTimeMillis();
            page.navigate(settingsUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            if (page.url().contains("/login")) {
                log.info("Сессия устарела, повторная авторизация...");
                login(context);
                page.navigate(settingsUrl);
                page.waitForLoadState(LoadState.NETWORKIDLE);
            }

            // Сохраняем актуальные куки
            saveSession(context);

            // Парсим таблицу ID городов
            int count = parseCityIds(page, shop);

            shop.setLastSyncedAt(ZonedDateTime.now());
            shopRepository.save(shop);

            log.info("Синхронизация завершена: {} записей для {}", count, shopName);
            return "Синхронизировано " + count + " записей для " + shopName;

        } catch (Exception e) {
            log.error("Ошибка синхронизации {}: {}", shopName, e.getMessage(), e);
            throw new RuntimeException("Ошибка синхронизации: " + e.getMessage(), e);
        }
    }

    /**
     * Авторизация через форму логина.
     * Сайт использует Spring Security: поля j_username / j_password,
     * форма отправляется на j_spring_security_check.
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

            // После успешного логина редиректит на главную, не на /login
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
     * Playwright Cookie не имеет дефолтного конструктора для Jackson,
     * поэтому десериализуем через Map и создаём объекты вручную.
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
     * Парсинг таблицы ID городов со страницы настроек.
     * Структура HTML:
     *   <tr><td>ID городов apteka-april.ru:</td>
     *       <td><input type="text" value="4400,3345,..."></td></tr>
     */
    private int parseCityIds(Page page, ZoomosShop shop) {
        List<ElementHandle> rows = page.querySelectorAll("tr");
        List<ZoomosCityId> toSave = new ArrayList<>();

        for (ElementHandle row : rows) {
            try {
                ElementHandle labelCell = row.querySelector("td:first-child");
                if (labelCell == null) continue;

                String labelText = labelCell.innerText().trim();
                if (!labelText.contains("ID городов")) continue;

                // "ID городов apteka-april.ru:" → "apteka-april.ru"
                String siteName = labelText.replace("ID городов", "").replace(":", "").trim();
                if (siteName.isEmpty()) continue;
                // Пропускаем строки где siteName содержит пробелы (несколько доменов или служебные поля)
                if (siteName.contains(" ")) continue;

                ElementHandle valueCell = row.querySelector("td:nth-child(2)");
                if (valueCell == null) continue;

                ElementHandle input = valueCell.querySelector("input[type='text']");
                String cityIds = input != null ? input.getAttribute("value") : valueCell.innerText().trim();
                if (cityIds == null) cityIds = "";

                toSave.add(ZoomosCityId.builder()
                        .shop(shop)
                        .siteName(siteName)
                        .cityIds(cityIds)
                        .isActive(true)
                        .build());

            } catch (Exception e) {
                log.warn("Ошибка парсинга строки таблицы: {}", e.getMessage());
            }
        }

        if (toSave.isEmpty()) {
            log.warn("Таблица ID городов не найдена на странице. URL: {}", page.url());
            return 0;
        }

        // Заменяем все записи для магазина: удаляем старые, вставляем новые
        cityIdRepository.deleteByShopId(shop.getId());
        cityIdRepository.saveAll(toSave);
        return toSave.size();
    }
}
