package com.java.service.handbook;

import com.java.dto.handbook.BhSearchConfigDto;
import com.java.dto.handbook.BhStatsDto;
import com.java.model.entity.*;
import com.java.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
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
    private final JdbcTemplate jdbc;

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
        //    Строки без штрихкода идут в withoutBarcode
        List<Map<String, String>> withBarcode = new ArrayList<>();
        List<Map<String, String>> withoutBarcode = new ArrayList<>();
        for (Map<String, String> r : rows) {
            String name = trim(r.get("name"));
            if (name == null || name.isEmpty()) continue;
            String barcodeRaw = trim(r.get("barcode"));
            if (barcodeRaw != null && !barcodeRaw.isEmpty()) {
                // Разбиваем и нормализуем — каждый ШК как отдельная строка
                for (String b : BarcodeUtils.parseAndNormalize(barcodeRaw)) {
                    Map<String, String> expanded = new HashMap<>(r);
                    expanded.put("barcode", b);
                    withBarcode.add(expanded);
                }
            } else {
                withoutBarcode.add(r);
            }
        }

        int saved = 0;

        // 2. Обработка строк со штрихкодом
        if (!withBarcode.isEmpty()) {
            // UPSERT продуктов по каждому штрихкоду
            List<Object[]> productParams = new ArrayList<>();
            for (Map<String, String> r : withBarcode) {
                productParams.add(new Object[]{
                        r.get("barcode"),
                        trim(r.get("brand")),
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

        // 3. Обработка строк без штрихкода — batch lookup по именам
        if (!withoutBarcode.isEmpty()) {
            // Собираем уникальные имена для batch-поиска
            Set<String> uniqueNames = withoutBarcode.stream()
                    .map(r -> trim(r.get("name")))
                    .filter(n -> n != null && !n.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            Map<String, Long> nameLowerToId = new HashMap<>();
            if (!uniqueNames.isEmpty()) {
                String inClause = String.join(",", Collections.nCopies(uniqueNames.size(), "LOWER(TRIM(?))"));
                List<String> nameList = new ArrayList<>(uniqueNames);
                jdbc.query(
                        "SELECT DISTINCT ON (LOWER(TRIM(n.name))) LOWER(TRIM(n.name)) AS name_key, n.product_id " +
                        "FROM bh_names n WHERE LOWER(TRIM(n.name)) IN (" + inClause + ")",
                        nameList.toArray(),
                        (RowCallbackHandler) rs -> nameLowerToId.put(rs.getString("name_key"), rs.getLong("product_id")));
            }

            // Создаём продукты для не найденных имён
            for (Map<String, String> r : withoutBarcode) {
                String name  = trim(r.get("name"));
                String brand = trim(r.get("brand"));
                String mfCode = trim(r.get("manufacturerCode"));
                if (name == null || name.isEmpty()) continue;

                String key = name.toLowerCase().trim();
                if (!nameLowerToId.containsKey(key)) {
                    Long pid = jdbc.queryForObject(
                            "INSERT INTO bh_products (brand, manufacturer_code, created_at, updated_at) " +
                            "VALUES (?, ?, NOW(), NOW()) RETURNING id",
                            Long.class, brand, mfCode);
                    jdbc.update(
                            "INSERT INTO bh_names (product_id, name, source, created_at) VALUES (?, ?, ?, NOW()) " +
                            "ON CONFLICT (product_id, name) DO NOTHING",
                            pid, name, source);
                    nameLowerToId.put(key, pid);
                } else if (brand != null && !brand.isEmpty()) {
                    Long pid = nameLowerToId.get(key);
                    jdbc.update(
                            "UPDATE bh_products SET brand = ?, updated_at = NOW() WHERE id = ? AND (brand IS NULL OR brand = '')",
                            brand, pid);
                }
                saved++;
            }
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

        for (String name : newNames) {
            // Ищем бренд из первой строки с таким именем
            String brand = rows.stream()
                    .filter(r -> name.equals(trim(r.get("name"))))
                    .map(r -> trim(r.get("brand")))
                    .filter(b -> b != null && !b.isEmpty())
                    .findFirst().orElse(null);
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

        // Обновляем реестр доменов одним батчем
        for (String domain : uniqueDomains) {
            jdbc.update(
                    "INSERT INTO bh_domains (domain, is_active, url_count, created_at) VALUES (?, true, 1, NOW()) " +
                    "ON CONFLICT (domain) DO UPDATE SET url_count = bh_domains.url_count + 1",
                    domain);
        }

        log.info("BH_NAME_URL батч: {} строк обработано", urlParams.size());
        return urlParams.size();
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
                // Ленивый поиск — ищем по имени в БД
                nameRepo.findByNameIgnoreCase(name).ifPresent(n -> {
                    BhProduct p = n.getProduct();
                    nameToProduct.put(key, p);
                    productIdsFromNames.add(p.getId());
                });
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

        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            List<String> bcs = rowBarcodes.get(i);

            String id   = getCol(row, idIdx);
            String name = nameIdx >= 0 ? getCol(row, nameIdx) : null;
            // Исходное (ненормализованное) значение штрихкода для отображения в результате
            String rawBarcode = barcodeIdx >= 0 ? getCol(row, barcodeIdx) : null;

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
                // Не найдено — добавляем строку с пустыми URL
                resultRows.add(new String[]{
                        id,
                        rawBarcode != null ? rawBarcode : "",
                        name != null ? name : "",
                        "", "", ""});
                continue;
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

        // Генерация файла
        if ("CSV".equalsIgnoreCase(config.getOutputFormat())) {
            return generateCsv(resultRows, config);
        } else {
            return generateExcel(resultRows);
        }
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
                domainRepo.count()
        );
    }

    // =========================================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ - Поиск/создание продуктов
    // =========================================================================

    private BhProduct findOrCreateByBarcode(String barcode, String brand, String mfCode) {
        try {
            return productRepo.findByBarcode(barcode)
                    .orElseGet(() -> createProduct(barcode, brand, mfCode));
        } catch (DataIntegrityViolationException e) {
            // Гонка при параллельном создании — повторный поиск
            return productRepo.findByBarcode(barcode)
                    .orElseThrow(() -> new RuntimeException("Не удалось найти/создать продукт по штрихкоду: " + barcode));
        }
    }

    private BhProduct findProductByName(String name, String brand, boolean requireBrandMatch) {
        Optional<BhName> bhName = nameRepo.findByNameIgnoreCase(name);
        if (bhName.isEmpty()) return null;
        BhProduct p = bhName.get().getProduct();
        if (requireBrandMatch && brand != null && !brand.isEmpty()) {
            if (!brand.equalsIgnoreCase(p.getBrand())) return null;
        }
        return p;
    }

    private BhProduct createProduct(String barcode, String brand, String mfCode) {
        BhProduct p = BhProduct.builder()
                .barcode(barcode)
                .brand(brand)
                .manufacturerCode(mfCode)
                .build();
        return productRepo.save(p);
    }

    private void addNameIfAbsent(BhProduct product, String name, String source) {
        if (name == null || name.isEmpty()) return;
        if (!nameRepo.existsByProductIdAndName(product.getId(), name)) {
            try {
                BhName bn = BhName.builder()
                        .product(product)
                        .name(name)
                        .source(source)
                        .build();
                nameRepo.save(bn);
            } catch (DataIntegrityViolationException e) {
                log.debug("BH: дублирование имени '{}' для продукта {} — пропуск", name, product.getId());
            }
        }
    }

    private void addUrlIfAbsent(BhProduct product, String url, String siteName, String source) {
        if (url == null || url.isEmpty()) return;
        String domain = extractDomain(url);

        if (!urlRepo.existsByProductIdAndUrl(product.getId(), url)) {
            try {
                BhUrl bu = BhUrl.builder()
                        .product(product)
                        .url(url)
                        .domain(domain)
                        .siteName(siteName)
                        .source(source)
                        .build();
                urlRepo.save(bu);
                if (domain != null) registerDomain(domain);
            } catch (DataIntegrityViolationException e) {
                log.debug("BH: дублирование URL '{}' для продукта {} — пропуск", url, product.getId());
            }
        }
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
