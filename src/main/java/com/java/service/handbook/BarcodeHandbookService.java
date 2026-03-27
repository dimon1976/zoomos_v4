package com.java.service.handbook;

import com.java.dto.handbook.BhSearchConfigDto;
import com.java.dto.handbook.BhStatsDto;
import com.java.dto.handbook.DomainRenameResult;
import com.java.model.entity.*;
import com.java.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;
import com.java.util.BarcodeUtils;

/**
 * Основной сервис справочника штрихкодов.
 * Обрабатывает два типа импорта (BH_BARCODE_NAME, BH_NAME_URL),
 * поиск по справочнику и управление доменами.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BarcodeHandbookService {

    private final BhProductRepository productRepo;
    private final BhNameRepository nameRepo;
    private final BhUrlRepository urlRepo;
    private final BhDomainRepository domainRepo;
    private final BhBrandService bhBrandService;
    private final JdbcTemplate jdbc;

    @Autowired @Lazy
    private BarcodeHandbookService self;

    // =========================================================================
    // ИМПОРТ: тип BH_BARCODE_NAME (штрихкод + наименование) — БАТЧ
    // =========================================================================

    /**
     * Батч-сохранение строк "штрихкод+наименование" через JDBC.
     * Один вызов = один батч всего набора строк (100-1000 записей).
     * В ~100x быстрее поштучного сохранения через JPA.
     */
    @Transactional
    public int persistBarcodeNameBatch(List<Map<String, String>> rows, String source) {
        if (rows.isEmpty()) return 0;

        // 1. Разворачиваем строки: один штрихкод из "a,b,c" → три отдельные записи
        //    Строки без штрихкода пропускаются — наименование без ШК не сохраняется
        List<Map<String, String>> withBarcode = new ArrayList<>();
        for (Map<String, String> r : rows) {
            String name = trim(r.get("name"));
            if (name == null || name.isEmpty()) continue;
            String barcodeRaw = trim(r.get("barcode"));
            if (barcodeRaw == null || barcodeRaw.isEmpty()) continue;
            // Разбиваем и нормализуем — каждый ШК как отдельная строка
            for (String b : BarcodeUtils.parseAndNormalize(barcodeRaw)) {
                if (BarcodeUtils.isInvalid(b)) {
                    log.warn("BH_BARCODE_NAME: отклонён бракованный ШК '{}' (наименование: {})", b, name);
                    continue;
                }
                Map<String, String> expanded = new HashMap<>(r);
                expanded.put("barcode", b);
                withBarcode.add(expanded);
            }
        }

        int saved = 0;

        // 2. Обработка строк со штрихкодом
        if (!withBarcode.isEmpty()) {
            // Сортировка по штрихкоду: предотвращает дедлоки при конкурентных импортах
            withBarcode.sort(Comparator.comparing(r -> r.get("barcode")));

            // Загружаем маппинг синонимов одним запросом для всего батча
            Set<String> rawBrandSet = withBarcode.stream()
                    .map(r -> trim(r.get("brand")))
                    .filter(b -> b != null && !b.isBlank())
                    .collect(Collectors.toSet());
            Map<String, String> synonymMap = bhBrandService.buildSynonymMap(rawBrandSet);

            // UPSERT продуктов по каждому штрихкоду
            List<Object[]> productParams = new ArrayList<>();
            for (Map<String, String> r : withBarcode) {
                String rawBrand = trim(r.get("brand"));
                String normalizedBrand = (rawBrand != null && !rawBrand.isBlank())
                        ? synonymMap.getOrDefault(rawBrand.trim().toLowerCase(), rawBrand)
                        : rawBrand;
                productParams.add(new Object[]{
                        r.get("barcode"),
                        normalizedBrand,
                        trim(r.get("manufacturerCode"))
                });
            }
            jdbc.batchUpdate(
                    "INSERT INTO bh_products (barcode, brand, manufacturer_code, created_at, updated_at) " +
                    "VALUES (?, ?, ?, NOW(), NOW()) " +
                    "ON CONFLICT (barcode) DO UPDATE SET " +
                    "  brand = COALESCE(NULLIF(EXCLUDED.brand,''), bh_products.brand), " +
                    "  manufacturer_code = COALESCE(NULLIF(EXCLUDED.manufacturer_code,''), bh_products.manufacturer_code), " +
                    "  updated_at = NOW()",
                    productParams);

            // Получаем id продуктов по штрихкодам
            List<String> barcodes = withBarcode.stream()
                    .map(r -> r.get("barcode"))
                    .distinct().collect(Collectors.toList());
            String inClause = String.join(",", Collections.nCopies(barcodes.size(), "?"));
            Map<String, Long> barcodeToId = new HashMap<>();
            jdbc.query(
                    "SELECT id, barcode FROM bh_products WHERE barcode IN (" + inClause + ")",
                    barcodes.toArray(),
                    rs -> { barcodeToId.put(rs.getString("barcode"), rs.getLong("id")); });

            // UPSERT наименований для каждого продукта
            List<Object[]> nameParams = new ArrayList<>();
            for (Map<String, String> r : withBarcode) {
                Long pid = barcodeToId.get(r.get("barcode"));
                if (pid == null) continue;
                String name = trim(r.get("name"));
                if (name == null || name.isEmpty()) continue;
                nameParams.add(new Object[]{pid, name, source});
            }
            if (!nameParams.isEmpty()) {
                jdbc.batchUpdate(
                        "INSERT INTO bh_names (product_id, name, source, created_at) VALUES (?, ?, ?, NOW()) " +
                        "ON CONFLICT (product_id, name) DO NOTHING",
                        nameParams);
            }
            saved += withBarcode.size();
        }

        log.info("BH_BARCODE_NAME батч: {} строк обработано", saved);
        return saved;
    }

    // =========================================================================
    // ИМПОРТ: тип BH_NAME_URL (наименование + URL) — БАТЧ
    // =========================================================================

    /**
     * Батч-сохранение строк "наименование+URL" через JDBC.
     */
    @Transactional
    public int persistNameUrlBatch(List<Map<String, String>> rows, String source) {
        if (rows.isEmpty()) return 0;

        // Собираем уникальные имена
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, String> r : rows) {
            String name = trim(r.get("name"));
            String url  = trim(r.get("url"));
            if (name != null && !name.isEmpty() && url != null && !url.isEmpty()) {
                names.add(name);
            }
        }
        if (names.isEmpty()) return 0;

        // Находим существующие продукты по именам — один batch IN-запрос
        Map<String, Long> nameLowerToProductId = new HashMap<>();
        List<String> nameList = new ArrayList<>(names);
        String inClause = String.join(",", Collections.nCopies(nameList.size(), "LOWER(TRIM(?))"));
        jdbc.query(
                "SELECT DISTINCT ON (LOWER(TRIM(n.name))) LOWER(TRIM(n.name)) AS name_key, n.product_id " +
                "FROM bh_names n WHERE LOWER(TRIM(n.name)) IN (" + inClause + ")",
                nameList.toArray(),
                (RowCallbackHandler) rs -> nameLowerToProductId.put(rs.getString("name_key"), rs.getLong("product_id")));

        // Создаём продукты для новых имён
        Set<String> newNames = new LinkedHashSet<>();
        for (Map<String, String> r : rows) {
            String name = trim(r.get("name"));
            String url  = trim(r.get("url"));
            if (name == null || name.isEmpty() || url == null || url.isEmpty()) continue;
            if (!nameLowerToProductId.containsKey(name.toLowerCase().trim())) {
                newNames.add(name);
            }
        }

        // Загружаем маппинг синонимов одним запросом
        Set<String> rawBrandSet = rows.stream()
                .map(r -> trim(r.get("brand")))
                .filter(b -> b != null && !b.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> synonymMap = bhBrandService.buildSynonymMap(rawBrandSet);

        for (String name : newNames) {
            // Ищем бренд из первой строки с таким именем
            String rawBrand = rows.stream()
                    .filter(r -> name.equals(trim(r.get("name"))))
                    .map(r -> trim(r.get("brand")))
                    .filter(b -> b != null && !b.isEmpty())
                    .findFirst().orElse(null);
            String brand = (rawBrand != null && !rawBrand.isBlank())
                    ? synonymMap.getOrDefault(rawBrand.trim().toLowerCase(), rawBrand)
                    : rawBrand;
            Long pid = jdbc.queryForObject(
                    "INSERT INTO bh_products (brand, created_at, updated_at) VALUES (?, NOW(), NOW()) RETURNING id",
                    Long.class, brand);
            jdbc.update(
                    "INSERT INTO bh_names (product_id, name, source, created_at) VALUES (?, ?, ?, NOW()) " +
                    "ON CONFLICT (product_id, name) DO NOTHING",
                    pid, name, source);
            nameLowerToProductId.put(name.toLowerCase().trim(), pid);
        }

        // Батч-вставка URL
        List<Object[]> urlParams = new ArrayList<>();
        Set<String> uniqueDomains = new LinkedHashSet<>();
        for (Map<String, String> r : rows) {
            String name     = trim(r.get("name"));
            String url      = trim(r.get("url"));
            String siteName = trim(r.get("siteName"));
            if (name == null || name.isEmpty() || url == null || url.isEmpty()) continue;
            Long pid = nameLowerToProductId.get(name.toLowerCase().trim());
            if (pid == null) continue;
            String domain = extractDomain(url);
            if (domain != null) uniqueDomains.add(domain);
            urlParams.add(new Object[]{pid, url, domain, siteName, source});
        }

        if (!urlParams.isEmpty()) {
            jdbc.batchUpdate(
                    "INSERT INTO bh_urls (product_id, url, domain, site_name, source, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, NOW()) ON CONFLICT (product_id, url) DO NOTHING",
                    urlParams);
        }

        // Обновляем реестр доменов: пересчёт реального url_count из bh_urls
        if (!uniqueDomains.isEmpty()) {
            String domainInClause = String.join(",", Collections.nCopies(uniqueDomains.size(), "?"));
            jdbc.update(
                    "INSERT INTO bh_domains (domain, is_active, url_count, created_at) " +
                    "SELECT domain, true, COUNT(*), NOW() FROM bh_urls " +
                    "WHERE domain IN (" + domainInClause + ") GROUP BY domain " +
                    "ON CONFLICT (domain) DO UPDATE SET url_count = EXCLUDED.url_count",
                    uniqueDomains.toArray());
        }

        log.info("BH_NAME_URL батч: {} строк обработано", urlParams.size());
        return urlParams.size();
    }

    // =========================================================================
    // ИМПОРТ: тип BH_FULL (штрихкод + наименование + URL) — БАТЧ
    // =========================================================================

    /**
     * Батч-сохранение строк "полный импорт": штрихкод + наименование + URL + бренд.
     * Обязательное поле: name. Хотя бы одно из barcode/url должно присутствовать.
     * При наличии barcode: продукт ищется/создаётся по нему.
     * При отсутствии barcode: продукт ищется/создаётся по имени (как в BH_NAME_URL).
     */
    @Transactional
    public int persistFullBatch(List<Map<String, String>> rows, String source) {
        if (rows.isEmpty()) return 0;

        // --- Строки с штрихкодом ---
        List<Map<String, String>> withBarcode = new ArrayList<>();
        // --- Строки без штрихкода, но с именем+URL ---
        List<Map<String, String>> withoutBarcode = new ArrayList<>();

        for (Map<String, String> r : rows) {
            String name       = trim(r.get("name"));
            String barcodeRaw = trim(r.get("barcode"));
            String url        = trim(r.get("url"));

            boolean hasName    = name != null && !name.isEmpty();
            boolean hasBarcode = barcodeRaw != null && !barcodeRaw.isEmpty();
            boolean hasUrl     = url != null && !url.isEmpty();

            // Нужно минимум 2 поля из 3
            if ((hasName ? 1 : 0) + (hasBarcode ? 1 : 0) + (hasUrl ? 1 : 0) < 2) continue;

            if (hasBarcode) {
                for (String b : BarcodeUtils.parseAndNormalize(barcodeRaw)) {
                    if (BarcodeUtils.isInvalid(b)) {
                        log.warn("BH_FULL: отклонён бракованный ШК '{}' (наименование: {})", b, name);
                        continue;
                    }
                    Map<String, String> expanded = new HashMap<>(r);
                    expanded.put("barcode", b);
                    withBarcode.add(expanded);
                }
            } else {
                // name + url без barcode — делегируем в persistNameUrlBatch
                withoutBarcode.add(r);
            }
        }

        int saved = 0;

        // --- Блок со штрихкодом: UPSERT продуктов ---
        if (!withBarcode.isEmpty()) {
            // Сортировка по штрихкоду: предотвращает дедлоки при конкурентных импортах
            withBarcode.sort(Comparator.comparing(r -> r.get("barcode")));

            // Загружаем маппинг синонимов одним запросом для всего батча
            Set<String> rawBrandSet = withBarcode.stream()
                    .map(r -> trim(r.get("brand")))
                    .filter(b -> b != null && !b.isBlank())
                    .collect(Collectors.toSet());
            Map<String, String> synonymMap = bhBrandService.buildSynonymMap(rawBrandSet);

            List<Object[]> productParams = new ArrayList<>();
            for (Map<String, String> r : withBarcode) {
                String rawBrand = trim(r.get("brand"));
                String normalizedBrand = (rawBrand != null && !rawBrand.isBlank())
                        ? synonymMap.getOrDefault(rawBrand.trim().toLowerCase(), rawBrand)
                        : rawBrand;
                productParams.add(new Object[]{
                        r.get("barcode"),
                        normalizedBrand,
                        trim(r.get("manufacturerCode"))
                });
            }
            jdbc.batchUpdate(
                    "INSERT INTO bh_products (barcode, brand, manufacturer_code, created_at, updated_at) " +
                    "VALUES (?, ?, ?, NOW(), NOW()) " +
                    "ON CONFLICT (barcode) DO UPDATE SET " +
                    "  brand = COALESCE(NULLIF(EXCLUDED.brand,''), bh_products.brand), " +
                    "  manufacturer_code = COALESCE(NULLIF(EXCLUDED.manufacturer_code,''), bh_products.manufacturer_code), " +
                    "  updated_at = NOW()",
                    productParams);

            List<String> barcodes = withBarcode.stream()
                    .map(r -> r.get("barcode")).distinct().collect(Collectors.toList());
            String inClause = String.join(",", Collections.nCopies(barcodes.size(), "?"));
            Map<String, Long> barcodeToId = new HashMap<>();
            jdbc.query(
                    "SELECT id, barcode FROM bh_products WHERE barcode IN (" + inClause + ")",
                    barcodes.toArray(),
                    rs -> { barcodeToId.put(rs.getString("barcode"), rs.getLong("id")); });

            // UPSERT наименований
            List<Object[]> nameParams = new ArrayList<>();
            for (Map<String, String> r : withBarcode) {
                Long pid = barcodeToId.get(r.get("barcode"));
                if (pid == null) continue;
                String name = trim(r.get("name"));
                if (name != null && !name.isEmpty()) {
                    nameParams.add(new Object[]{pid, name, source});
                }
            }
            if (!nameParams.isEmpty()) {
                jdbc.batchUpdate(
                        "INSERT INTO bh_names (product_id, name, source, created_at) VALUES (?, ?, ?, NOW()) " +
                        "ON CONFLICT (product_id, name) DO NOTHING",
                        nameParams);
            }

            // UPSERT URL
            List<Object[]> urlParams = new ArrayList<>();
            Set<String> uniqueDomains = new LinkedHashSet<>();
            for (Map<String, String> r : withBarcode) {
                Long pid = barcodeToId.get(r.get("barcode"));
                if (pid == null) continue;
                String url = trim(r.get("url"));
                if (url == null || url.isEmpty()) continue;
                String siteName = trim(r.get("siteName"));
                String domain = extractDomain(url);
                if (domain != null) uniqueDomains.add(domain);
                urlParams.add(new Object[]{pid, url, domain, siteName, source});
            }
            if (!urlParams.isEmpty()) {
                jdbc.batchUpdate(
                        "INSERT INTO bh_urls (product_id, url, domain, site_name, source, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, NOW()) ON CONFLICT (product_id, url) DO NOTHING",
                        urlParams);
            }
            // Обновляем реестр доменов: пересчёт реального url_count из bh_urls
            if (!uniqueDomains.isEmpty()) {
                String domainInClause = String.join(",", Collections.nCopies(uniqueDomains.size(), "?"));
                jdbc.update(
                        "INSERT INTO bh_domains (domain, is_active, url_count, created_at) " +
                        "SELECT domain, true, COUNT(*), NOW() FROM bh_urls " +
                        "WHERE domain IN (" + domainInClause + ") GROUP BY domain " +
                        "ON CONFLICT (domain) DO UPDATE SET url_count = EXCLUDED.url_count",
                        uniqueDomains.toArray());
            }
            saved += withBarcode.size();
        }

        // --- Блок без штрихкода: делегируем в persistNameUrlBatch ---
        if (!withoutBarcode.isEmpty()) {
            saved += persistNameUrlBatch(withoutBarcode, source);
        }

        log.info("BH_FULL батч: {} строк обработано", saved);
        return saved;
    }

    // =========================================================================
    // Старые поштучные методы (оставлены для совместимости, не используются при батч-импорте)
    // =========================================================================

    @Transactional
    public void persistBarcodeNameRow(Map<String, String> row, String source) {
        persistBarcodeNameBatch(List.of(row), source);
    }

    @Transactional
    public void persistNameUrlRow(Map<String, String> row, String source) {
        persistNameUrlBatch(List.of(row), source);
    }

    // =========================================================================
    // ПОИСК И ЭКСПОРТ
    // =========================================================================

    /**
     * Поиск по справочнику и генерация результирующего файла.
     *
     * @param metadata  FileMetadata с путём к файлу запроса и его параметрами
     * @param config    параметры поиска (колонки, фильтр доменов, формат вывода)
     * @return байты готового файла для скачивания
     */
    @Transactional(readOnly = true)
    public byte[] searchAndExport(com.java.model.entity.FileMetadata metadata, BhSearchConfigDto config) throws IOException {
        // Читаем файл запроса (только данные, без заголовка)
        List<List<String>> rows = readFullFile(metadata);
        List<String> headers = parseHeaders(metadata.getColumnHeaders());

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Файл не содержит данных для поиска");
        }

        int idIdx      = findColumnIndex(headers, config.getIdColumn());
        int barcodeIdx = findColumnIndex(headers, config.getBarcodeColumn());
        int nameIdx    = findColumnIndex(headers, config.getNameColumn());
        int brandIdx   = findColumnIndex(headers, config.getBrandColumn());

        if (idIdx < 0) {
            throw new IllegalArgumentException("Не найдена колонка ID: " + config.getIdColumn());
        }

        // --- Batch поиск по штрихкодам (с нормализацией и разбивкой по запятой) ---
        // rowBarcodes[i] = список нормализованных ШК для строки i (может быть пустым)
        List<List<String>> rowBarcodes = new ArrayList<>();
        List<String> allBarcodes = new ArrayList<>();
        for (List<String> row : rows) {
            List<String> bc = barcodeIdx >= 0
                    ? BarcodeUtils.parseAndNormalize(getCol(row, barcodeIdx))
                    : Collections.emptyList();
            rowBarcodes.add(bc);
            allBarcodes.addAll(bc);
        }

        Map<String, BhProduct> barcodeToProduct = new HashMap<>();
        if (!allBarcodes.isEmpty()) {
            productRepo.findByBarcodeIn(allBarcodes)
                    .forEach(p -> barcodeToProduct.put(p.getBarcode(), p));
        }

        // Собираем все productIds для batch-загрузки URL
        Set<Long> productIds = new HashSet<>(barcodeToProduct.values()
                .stream().map(BhProduct::getId).collect(Collectors.toSet()));

        // Дополнительный поиск по именам для строк, где штрихкод не дал результата
        List<Long> productIdsFromNames = new ArrayList<>();
        Map<String, BhProduct> nameToProduct = new HashMap<>();
        if (nameIdx >= 0) {
            for (int i = 0; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                List<String> bcs = rowBarcodes.get(i);
                // Пропускаем строку, если хотя бы один штрихкод найден в справочнике
                boolean foundByBarcode = bcs.stream().anyMatch(barcodeToProduct::containsKey);
                if (foundByBarcode) continue;
                String name = getCol(row, nameIdx);
                if (name == null || name.isEmpty()) continue;
                String key = name.toLowerCase().trim();
                if (nameToProduct.containsKey(key)) continue; // уже нашли
                // Ленивый поиск — ищем по имени в БД (берём первый из возможных дублей)
                List<BhName> nameMatches = nameRepo.findByNameIgnoreCase(name);
                if (!nameMatches.isEmpty()) {
                    BhProduct p = nameMatches.get(0).getProduct();
                    nameToProduct.put(key, p);
                    productIdsFromNames.add(p.getId());
                }
            }
            productIds.addAll(productIdsFromNames);
        }

        // Batch загрузка URL с фильтром по доменам
        List<BhUrl> urls;
        List<String> domains = config.getDomainFilter();
        List<Long> productIdsList = new ArrayList<>(productIds);

        if (productIdsList.isEmpty()) {
            urls = Collections.emptyList();
        } else if (domains != null && !domains.isEmpty()) {
            urls = urlRepo.findByProductIdInAndDomainIn(productIdsList, domains);
        } else {
            urls = urlRepo.findByProductIdIn(productIdsList);
        }

        // Группировка URL по productId
        Map<Long, List<BhUrl>> urlsByProduct = urls.stream()
                .collect(Collectors.groupingBy(u -> u.getProduct().getId()));

        // Формирование результатов
        List<String[]> resultRows = new ArrayList<>();
        resultRows.add(new String[]{"ID", "Штрихкод", "Наименование", "Бренд", "Домен", "URL"});

        List<Object[]> synonymParams = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            List<String> bcs = rowBarcodes.get(i);

            String id   = getCol(row, idIdx);
            String name = nameIdx >= 0 ? getCol(row, nameIdx) : null;

            // Ищем продукт: сначала по нормализованным штрихкодам, потом по имени
            BhProduct product = null;
            String foundBarcode = null; // нормализованный ШК, по которому нашли продукт
            for (String bc : bcs) {
                BhProduct p = barcodeToProduct.get(bc);
                if (p != null) {
                    product = p;
                    foundBarcode = bc;
                    break;
                }
            }
            if (product == null && name != null && !name.isEmpty()) {
                product = nameToProduct.get(name.toLowerCase().trim());
            }

            if (product == null) {
                continue; // не найдено — пропускаем строку
            }

            if (config.isSaveNamesAsSynonyms() && name != null && !name.isEmpty()) {
                synonymParams.add(new Object[]{product.getId(), name, "search"});
            }

            // Для отображения штрихкода: предпочитаем найденный нормализованный ШК,
            // иначе используем штрихкод из записи продукта
            String displayBarcode = foundBarcode != null ? foundBarcode
                    : (product.getBarcode() != null ? product.getBarcode() : "");

            List<BhUrl> productUrls = urlsByProduct.getOrDefault(product.getId(), Collections.emptyList());
            if (productUrls.isEmpty()) {
                resultRows.add(new String[]{
                        id,
                        displayBarcode,
                        name != null ? name : "",
                        product.getBrand() != null ? product.getBrand() : "",
                        "", ""});
            } else {
                for (BhUrl u : productUrls) {
                    resultRows.add(new String[]{
                            id,
                            displayBarcode,
                            name != null ? name : "",
                            product.getBrand() != null ? product.getBrand() : "",
                            u.getDomain() != null ? u.getDomain() : "",
                            u.getUrl()});
                }
            }
        }

        log.info("BH search: {} input rows → {} output rows", rows.size(), resultRows.size() - 1);

        if (!synonymParams.isEmpty()) {
            self.saveSynonyms(synonymParams);
        }

        // Генерация файла
        if ("CSV".equalsIgnoreCase(config.getOutputFormat())) {
            return generateCsv(resultRows, config);
        } else {
            return generateExcel(resultRows);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSynonyms(List<Object[]> params) {
        jdbc.batchUpdate(
            "INSERT INTO bh_names (product_id, name, source, created_at) " +
            "VALUES (?, ?, ?, NOW()) ON CONFLICT (product_id, name) DO NOTHING",
            params);
        log.info("BH search: сохранено {} синонимов наименований (уже существующие пропущены)", params.size());
    }

    // =========================================================================
    // ПОИСК В UI (AJAX)
    // =========================================================================

    /**
     * Поиск по справочнику для отображения в UI.
     * Ищет по штрихкоду (точное совпадение), части наименования или части URL.
     * Возвращает список Map для сериализации в JSON.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchForUi(String query) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        String q = query.trim();

        List<Map<String, Object>> results = new ArrayList<>();
        Set<Long> foundIds = new HashSet<>();

        // 1. Поиск продуктов по штрихкоду (поддержка нескольких ШК через запятую)
        List<String> barcodes = BarcodeUtils.parseAndNormalize(q);
        for (String bc : barcodes) {
            productRepo.findByBarcode(bc).ifPresent(p -> {
                if (foundIds.add(p.getId())) {
                    results.add(buildProductResult(p, "barcode"));
                }
            });
        }

        // 2. Поиск по части наименования через JDBC (ILIKE)
        String nameLike = "%" + q.toLowerCase() + "%";
        jdbc.query(
                "SELECT DISTINCT n.product_id FROM bh_names n WHERE LOWER(n.name) LIKE ? LIMIT 30",
                (RowCallbackHandler) rs -> {
                    long pid = rs.getLong("product_id");
                    if (foundIds.add(pid)) {
                        productRepo.findById(pid).ifPresent(p ->
                                results.add(buildProductResult(p, "name"))
                        );
                    }
                }, nameLike);

        // 3. Поиск по части URL через JDBC
        String urlLike = "%" + q.toLowerCase() + "%";
        jdbc.query(
                "SELECT DISTINCT u.product_id FROM bh_urls u WHERE LOWER(u.url) LIKE ? LIMIT 30",
                (RowCallbackHandler) rs -> {
                    long pid = rs.getLong("product_id");
                    if (foundIds.add(pid)) {
                        productRepo.findById(pid).ifPresent(p ->
                                results.add(buildProductResult(p, "url"))
                        );
                    }
                }, urlLike);

        return results;
    }

    private static final int MAX_NAMES_IN_UI = 7;

    private Map<String, Object> buildProductResult(BhProduct p, String matchedBy) {
        // Имена продукта — ограничиваем для читаемости UI
        List<String> allNames = nameRepo.findByProductIdIn(List.of(p.getId()))
                .stream().map(BhName::getName).collect(Collectors.toList());
        List<String> names = allNames.size() > MAX_NAMES_IN_UI
                ? allNames.subList(0, MAX_NAMES_IN_UI)
                : allNames;

        // Ссылки продукта
        List<Map<String, String>> urls = urlRepo.findByProductIdIn(List.of(p.getId()))
                .stream().map(u -> {
                    Map<String, String> m = new java.util.LinkedHashMap<>();
                    m.put("url", u.getUrl());
                    m.put("domain", u.getDomain() != null ? u.getDomain() : "");
                    return m;
                }).collect(Collectors.toList());

        // При поиске по наименованию: если у найденного продукта нет URL,
        // ищем URL у всех продуктов с теми же наименованиями (данные могут быть фрагментированы)
        if (urls.isEmpty() && "name".equals(matchedBy) && !allNames.isEmpty()) {
            List<String> nameKeys = allNames.stream()
                    .map(n -> n.toLowerCase().trim())
                    .distinct()
                    .collect(Collectors.toList());
            String ph = String.join(",", Collections.nCopies(nameKeys.size(), "?"));
            Set<Long> relatedIds = new HashSet<>();
            relatedIds.add(p.getId());
            jdbc.query(
                "SELECT DISTINCT n2.product_id FROM bh_names n2 WHERE LOWER(TRIM(n2.name)) IN (" + ph + ")",
                nameKeys.toArray(),
                (RowCallbackHandler) rs -> relatedIds.add(rs.getLong("product_id")));
            if (relatedIds.size() > 1) {
                urls = urlRepo.findByProductIdIn(new ArrayList<>(relatedIds))
                        .stream().map(u -> {
                            Map<String, String> m = new java.util.LinkedHashMap<>();
                            m.put("url", u.getUrl());
                            m.put("domain", u.getDomain() != null ? u.getDomain() : "");
                            return m;
                        }).collect(Collectors.toList());
            }
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("productId", p.getId());
        result.put("barcode", p.getBarcode() != null ? p.getBarcode() : "");
        result.put("brand", p.getBrand() != null ? p.getBrand() : "");
        result.put("names", names);
        result.put("totalNames", allNames.size());
        result.put("urls", urls);
        result.put("matchedBy", matchedBy);
        return result;
    }

    // =========================================================================
    // УПРАВЛЕНИЕ ДОМЕНАМИ
    // =========================================================================

    public List<BhDomain> getAllDomains() {
        return domainRepo.findAllByOrderByUrlCountDesc();
    }

    public List<BhDomain> searchDomains(String query) {
        if (query == null || query.isBlank()) return getAllDomains();
        return domainRepo.searchByDomain(query.trim());
    }

    @Transactional
    public BhDomain toggleDomain(Long id) {
        BhDomain d = domainRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Домен не найден: " + id));
        d.setIsActive(!d.getIsActive());
        return domainRepo.save(d);
    }

    public BhStatsDto getStats() {
        return new BhStatsDto(
                productRepo.count(),
                nameRepo.count(),
                urlRepo.count(),
                domainRepo.count(),
                bhBrandService.countBrands()
        );
    }

    /**
     * Пересчитывает url_count для всех доменов на основе реального количества URL в bh_urls.
     * Используется для исправления исторических данных.
     */
    @Transactional
    public int recalculateDomainCounts() {
        return jdbc.update(
                "UPDATE bh_domains d " +
                "SET url_count = (SELECT COUNT(*) FROM bh_urls u WHERE u.domain = d.domain)");
    }

    private String extractDomain(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            String u = url.trim();
            if (!u.contains("://")) u = "https://" + u;
            String host = URI.create(u).getHost();
            if (host == null) return null;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            log.debug("Не удалось извлечь домен из URL: {}", url);
            return null;
        }
    }

    /**
     * Предварительный просмотр переименования домена: считает затронутые URL и дубли.
     */
    @Transactional(readOnly = true)
    public DomainRenameResult previewRenameDomain(Long domainId, String newDomainName) {
        BhDomain domain = domainRepo.findById(domainId)
                .orElseThrow(() -> new IllegalArgumentException("Домен не найден: " + domainId));
        String oldDomain = domain.getDomain();
        String newDomain = newDomainName.trim().toLowerCase();

        // Считаем дубли
        Integer deletedDuplicates = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bh_urls u WHERE u.domain = ? " +
                "AND EXISTS (SELECT 1 FROM bh_urls b2 WHERE b2.product_id = u.product_id " +
                "AND b2.url = REPLACE(u.url, '://' || ?, '://' || ?) AND b2.id != u.id)",
                Integer.class, oldDomain, oldDomain, newDomain);

        Integer totalUrls = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bh_urls WHERE domain = ?", Integer.class, oldDomain);

        boolean merged = domainRepo.findByDomain(newDomain).isPresent()
                && !newDomain.equals(oldDomain);

        return new DomainRenameResult(
                totalUrls != null ? totalUrls : 0,
                deletedDuplicates != null ? deletedDuplicates : 0,
                merged,
                newDomain
        );
    }

    /**
     * Переименовывает/объединяет домен: обновляет URL, удаляет дубли, обновляет реестр доменов.
     */
    @Transactional
    public DomainRenameResult renameDomain(Long domainId, String newDomainName) {
        BhDomain domain = domainRepo.findById(domainId)
                .orElseThrow(() -> new IllegalArgumentException("Домен не найден: " + domainId));
        String oldDomain = domain.getDomain();
        String newDomain = newDomainName.trim().toLowerCase();

        if (oldDomain.equals(newDomain)) {
            throw new IllegalArgumentException("Новое название совпадает с текущим");
        }

        // 1. Удалить дубликаты (те URL, которые после rename совпадут с существующим URL того же продукта)
        int deletedDuplicates = jdbc.update(
                "DELETE FROM bh_urls WHERE domain = ? " +
                "AND EXISTS (SELECT 1 FROM bh_urls b2 WHERE b2.product_id = bh_urls.product_id " +
                "AND b2.url = REPLACE(bh_urls.url, '://' || ?, '://' || ?) AND b2.id != bh_urls.id)",
                oldDomain, oldDomain, newDomain);

        // 2. Обновить URL и domain
        int updatedUrls = jdbc.update(
                "UPDATE bh_urls SET url = REPLACE(url, '://' || ?, '://' || ?), domain = ? WHERE domain = ?",
                oldDomain, newDomain, newDomain, oldDomain);

        // 3. Обновить реестр доменов
        boolean merged = false;
        Optional<BhDomain> existingTarget = domainRepo.findByDomain(newDomain);

        // Пересчитываем реальный url_count из bh_urls после обновления
        Long actualCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bh_urls WHERE domain = ?", Long.class, newDomain);
        long realCount = actualCount != null ? actualCount : 0;

        if (existingTarget.isPresent()) {
            BhDomain target = existingTarget.get();
            target.setUrlCount(realCount);
            domainRepo.save(target);
            domainRepo.delete(domain);
            merged = true;
        } else {
            domain.setDomain(newDomain);
            domain.setUrlCount(realCount);
            domainRepo.save(domain);
        }

        log.info("Домен '{}' → '{}': обновлено {} URL, удалено {} дублей, слияние={}",
                oldDomain, newDomain, updatedUrls, deletedDuplicates, merged);
        return new DomainRenameResult(updatedUrls, deletedDuplicates, merged, newDomain);
    }

    @Transactional
    public void registerDomain(String domain) {
        if (domain == null || domain.isBlank()) return;
        if (domainRepo.findByDomain(domain).isEmpty()) {
            try {
                domainRepo.save(BhDomain.builder()
                        .domain(domain)
                        .isActive(true)
                        .urlCount(1L)
                        .build());
            } catch (DataIntegrityViolationException e) {
                domainRepo.incrementUrlCount(domain);
            }
        } else {
            domainRepo.incrementUrlCount(domain);
        }
    }

    // =========================================================================
    // ОЧИСТКА ДАННЫХ
    // =========================================================================

    /**
     * Статистика некорректных данных для страницы очистки.
     * Использует LIMIT-сэмплинг вместо COUNT(*): PostgreSQL останавливается при нахождении
     * первых N строк, что многократно быстрее полного сканирования таблицы.
     */
    public Map<String, Object> getCleanupStats() {
        // Научная нотация: ШК содержит 'E' или 'e' (невозможно для числового ШК)
        List<String> invalidExamples = jdbc.queryForList(
            "SELECT barcode FROM bh_products WHERE barcode ~ '[Ee]' LIMIT 10",
            String.class);

        // Короткие/нечисловые: содержит не-цифры или длина < 6
        List<String> suspectExamples = jdbc.queryForList(
            "SELECT barcode FROM bh_products WHERE barcode IS NOT NULL " +
            "AND (LENGTH(barcode) < 6 OR barcode ~ '[^0-9]') LIMIT 10",
            String.class);

        // Сироты: NULL barcode + нет URL (проверяем наличие хотя бы одного через LIMIT 1)
        List<Long> orphanCheck = jdbc.queryForList(
            "SELECT id FROM bh_products WHERE barcode IS NULL " +
            "AND NOT EXISTS (SELECT 1 FROM bh_urls WHERE product_id = bh_products.id) LIMIT 1",
            Long.class);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("invalidBarcodeCount", invalidExamples.size());
        stats.put("invalidExamples", invalidExamples);
        stats.put("invalidHasMore", invalidExamples.size() == 10);
        stats.put("suspectCount", suspectExamples.size());
        stats.put("suspectExamples", suspectExamples);
        stats.put("suspectHasMore", suspectExamples.size() == 10);
        stats.put("orphanCount", orphanCheck.isEmpty() ? 0 : 1);
        return stats;
    }

    /**
     * Удалить продукты с ШК в научной нотации (94172E+12 и т.п.).
     * Каскадно удаляются bh_names (ON DELETE CASCADE).
     */
    @Transactional
    public int deleteInvalidBarcodeProducts() {
        int count = jdbc.update(
            "DELETE FROM bh_products WHERE barcode ~ '[Ee]'");
        log.info("Очистка: удалено {} продуктов с бракованными ШК (научная нотация)", count);
        return count;
    }

    /**
     * Удалить продукты-сироты: NULL штрихкод + нет URL.
     */
    @Transactional
    public int deleteOrphanProducts() {
        int count = jdbc.update(
            "DELETE FROM bh_products WHERE barcode IS NULL " +
            "AND NOT EXISTS (SELECT 1 FROM bh_urls WHERE product_id = bh_products.id)");
        log.info("Очистка: удалено {} продуктов-сирот (NULL ШК + нет URL)", count);
        return count;
    }

    /**
     * Удалить продукты с нечисловыми или короткими ШК.
     *
     * @param minLength минимальная длина ШК (по умолчанию 6)
     */
    @Transactional
    public int deleteSuspectBarcodeProducts(int minLength) {
        int count = jdbc.update(
            "DELETE FROM bh_products WHERE barcode IS NOT NULL " +
            "AND (LENGTH(barcode) < ? OR barcode ~ '[^0-9]')",
            minLength);
        log.info("Очистка: удалено {} продуктов с подозрительными ШК (minLength={})", count, minLength);
        return count;
    }

    // =========================================================================
    // ЧТЕНИЕ ФАЙЛОВ
    // =========================================================================

    public List<List<String>> readFullFile(FileMetadata metadata) throws IOException {
        Path path = Path.of(metadata.getTempFilePath());
        String ext = metadata.getOriginalFilename().toLowerCase();
        if (ext.endsWith(".xlsx") || ext.endsWith(".xls")) {
            return readExcelFile(path);
        } else {
            return readCsvFile(path, metadata);
        }
    }

    private List<List<String>> readExcelFile(Path path) throws IOException {
        List<List<String>> data = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (FileInputStream fis = new FileInputStream(path.toFile());
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean isFirst = true;
            for (Row row : sheet) {
                if (isFirst) { isFirst = false; continue; }
                List<String> rowData = new ArrayList<>();
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i);
                    String value = "";
                    if (cell != null) {
                        if (cell.getCellType() == CellType.NUMERIC) {
                            value = String.valueOf((long) cell.getNumericCellValue());
                        } else {
                            value = formatter.formatCellValue(cell);
                        }
                    }
                    rowData.add(value);
                }
                data.add(rowData);
            }
        }
        return data;
    }

    private List<List<String>> readCsvFile(Path path, FileMetadata metadata) throws IOException {
        List<List<String>> data = new ArrayList<>();
        String encoding = metadata.getDetectedEncoding() != null ? metadata.getDetectedEncoding() : "UTF-8";
        String delimStr = metadata.getDetectedDelimiter() != null ? metadata.getDetectedDelimiter() : ";";
        char delim = delimStr.isEmpty() ? ';' : delimStr.charAt(0);
        try (com.opencsv.CSVReader reader = new com.opencsv.CSVReaderBuilder(
                Files.newBufferedReader(path, Charset.forName(encoding)))
                .withCSVParser(new com.opencsv.CSVParserBuilder().withSeparator(delim).build())
                .build()) {
            String[] line;
            boolean isFirst = true;
            while ((line = reader.readNext()) != null) {
                if (isFirst) { isFirst = false; continue; }
                data.add(Arrays.asList(line));
            }
        } catch (com.opencsv.exceptions.CsvValidationException e) {
            throw new IOException("Ошибка парсинга CSV: " + e.getMessage(), e);
        }
        return data;
    }

    private List<String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) return new ArrayList<>();
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return om.readValue(headersJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private int findColumnIndex(List<String> headers, String columnName) {
        if (columnName == null || columnName.isBlank() || headers == null) return -1;
        for (int i = 0; i < headers.size(); i++) {
            if (columnName.equalsIgnoreCase(headers.get(i))) return i;
        }
        // Попробуем как числовой индекс
        try {
            int idx = Integer.parseInt(columnName);
            return (idx >= 0 && idx < headers.size()) ? idx : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String getCol(List<String> row, int idx) {
        if (idx < 0 || idx >= row.size()) return null;
        String v = row.get(idx);
        return v != null ? v.trim() : null;
    }

    private String trim(String s) {
        return s != null ? s.trim() : null;
    }

    // =========================================================================
    // ГЕНЕРАЦИЯ ФАЙЛОВ РЕЗУЛЬТАТА
    // =========================================================================

    private byte[] generateCsv(List<String[]> rows, BhSearchConfigDto config) throws IOException {
        String delim = config.getCsvDelimiter() != null ? config.getCsvDelimiter() : ";";
        Charset cs = "UTF-8".equalsIgnoreCase(config.getCsvEncoding()) ? StandardCharsets.UTF_8 : Charset.forName("windows-1251");
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, cs)) {
            for (String[] row : rows) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) sb.append(delim);
                    sb.append(escapeCsv(row[i], delim));
                }
                writer.write(sb.toString());
                writer.write("\n");
            }
            writer.flush();
            return out.toByteArray();
        }
    }

    private byte[] generateExcel(List<String[]> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Результаты");

            // Стиль заголовка
            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            for (int ri = 0; ri < rows.size(); ri++) {
                Row row = sheet.createRow(ri);
                String[] data = rows.get(ri);
                for (int ci = 0; ci < data.length; ci++) {
                    Cell cell = row.createCell(ci);
                    cell.setCellValue(data[ci] != null ? data[ci] : "");
                    if (ri == 0) cell.setCellStyle(headerStyle);
                }
            }

            // Авто-ширина первых 6 колонок
            for (int i = 0; i < 6; i++) {
                sheet.setColumnWidth(i, 256 * 25);
            }
            sheet.setColumnWidth(5, 256 * 60); // URL шире

            wb.write(out);
            return out.toByteArray();
        }
    }

    private String escapeCsv(String value, String delim) {
        if (value == null) return "";
        if (value.contains(delim) || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
