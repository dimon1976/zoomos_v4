package com.java.service.imports.handlers;

import com.java.model.entity.ImportSession;
import com.java.model.enums.DataSourceType;
import com.java.model.enums.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Сервис для сохранения импортированных данных в БД
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EntityPersistenceService {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private static final List<String> AV_DATA_PARAMS = List.of(
            "dataSource", "operationId", "clientId", "productId", "productName",
            "productBrand", "productBar", "productDescription", "productUrl",
            "productCategory1", "productCategory2", "productCategory3",
            "productPrice", "productAnalog", "productAdditional1",
            "productAdditional2", "productAdditional3", "productAdditional4",
            "productAdditional5", "productAdditional6", "productAdditional7",
            "productAdditional8", "productAdditional9", "productAdditional10",
            "region", "regionAddress", "competitorName",
            "competitorPrice", "competitorPromotionalPrice", "competitorTime",
            "competitorDate", "competitorLocalDateTime", "competitorStockStatus",
            "competitorAdditionalPrice", "competitorCommentary",
            "competitorProductName", "competitorAdditional",
            "competitorAdditional2", "competitorAdditional3", "competitorAdditional4",
            "competitorAdditional5", "competitorAdditional6", "competitorAdditional7",
            "competitorAdditional8", "competitorAdditional9", "competitorAdditional10",
            "competitorUrl", "competitorWebCacheUrl",
            // Новые поля конкурентов (V19)
            "competitorBrand", "competitorCategory1", "competitorCategory2",
            "competitorCategory3", "competitorCategory4", "competitorProductId",
            "competitorBar", "competitorOldPrice",
            // Новые поля региона (V19)
            "regionCountry",
            // Системные поля (V19)
            "zmsId",
            "created_at", "updated_at"
    );

    private static final List<String> AV_HANDBOOK_PARAMS = List.of(
            "handbookRetailNetworkCode", "handbookRetailNetwork", "handbookPhysicalAddress", "handbookPriceZoneCode", "handbookWebSite", "handbookRegionCode", "handbookRegionName", "createdAt", "updatedAt"
    );


    /**
     * Сохраняет батч записей в БД
     */
    @Transactional
    public int saveBatch(List<Map<String, Object>> batch, EntityType entityType, ImportSession session) {
        if (batch.isEmpty()) return 0;

        switch (entityType) {
            case AV_DATA:
                return saveAvData(batch, session);
            case AV_HANDBOOK:
                return saveAvHandbook(batch, session);
            default:
                throw new UnsupportedOperationException("Неподдерживаемый тип сущности: " + entityType);
        }
    }

    /**
     * Сохраняет продукты
     */
    private int saveAvData(List<Map<String, Object>> batch, ImportSession session) {
        String sql = "INSERT INTO av_data (data_source, operation_id, client_id, product_id, product_name, product_brand, product_bar, product_description, product_url, product_category1, product_category2, product_category3," +
                " product_price, product_analog, product_additional1, product_additional2, product_additional3, product_additional4, product_additional5, product_additional6, product_additional7, product_additional8, product_additional9, product_additional10," +
                " region, region_Address, competitor_Name, competitor_Price," +
                " competitor_Promotional_Price, competitor_time, competitor_date, competitor_local_date_time, competitor_stock_status, competitor_additional_price, competitor_commentary, competitor_product_name," +
                " competitor_additional, competitor_additional2, competitor_additional3, competitor_additional4, competitor_additional5, competitor_additional6, competitor_additional7, competitor_additional8, competitor_additional9, competitor_additional10," +
                " competitor_url, competitor_web_cache_url," +
                // Новые поля конкурентов (V19)
                " competitor_brand, competitor_category1, competitor_category2, competitor_category3, competitor_category4, competitor_product_id, competitor_bar, competitor_old_price," +
                // Новые поля региона (V19)
                " region_country," +
                // Системные поля (V19)
                " zms_id," +
                " created_at, updated_at) " +
                "VALUES (:dataSource, :operationId, :clientId, :productId, :productName, :productBrand, :productBar, :productDescription, :productUrl, :productCategory1, :productCategory2, :productCategory3, :productPrice," +
                " :productAnalog, :productAdditional1, :productAdditional2, :productAdditional3, :productAdditional4, :productAdditional5, :productAdditional6, :productAdditional7, :productAdditional8, :productAdditional9, :productAdditional10," +
                " :region, :regionAddress, :competitorName, :competitorPrice, :competitorPromotionalPrice," +
                " :competitorTime, :competitorDate, :competitorLocalDateTime, :competitorStockStatus, :competitorAdditionalPrice, :competitorCommentary, :competitorProductName," +
                " :competitorAdditional, :competitorAdditional2, :competitorAdditional3, :competitorAdditional4, :competitorAdditional5, :competitorAdditional6, :competitorAdditional7, :competitorAdditional8, :competitorAdditional9, :competitorAdditional10," +
                " :competitorUrl, :competitorWebCacheUrl," +
                // Новые поля конкурентов (V19)
                " :competitorBrand, :competitorCategory1, :competitorCategory2, :competitorCategory3, :competitorCategory4, :competitorProductId, :competitorBar, :competitorOldPrice," +
                // Новые поля региона (V19)
                " :regionCountry," +
                // Системные поля (V19)
                " :zmsId," +
                " :created_at, :updated_at)";

        // Добавляем системные поля
        LocalDateTime now = LocalDateTime.now();
        Long operationId = session.getFileOperation().getId();
        Long clientId = session.getFileOperation().getClient() != null
                ? session.getFileOperation().getClient().getId()
                : null;

        batch.forEach(avData -> {
            // Заполняем отсутствующие ключи значением null, чтобы
            // NamedParameterJdbcTemplate не выбрасывал исключение
            AV_DATA_PARAMS.forEach(param -> avData.putIfAbsent(param, null));


            String dataSource = session.getTemplate() != null && session.getTemplate().getDataSourceType() != null
                    ? session.getTemplate().getDataSourceType().name()
                    : DataSourceType.FILE.name();
            avData.putIfAbsent("dataSource", dataSource); // Источник данных
            avData.putIfAbsent("operationId", operationId);
            avData.putIfAbsent("clientId", clientId);
            avData.putIfAbsent("created_at", now);
            avData.putIfAbsent("updated_at", now);

            // Преобразуем null значения в дефолтные
            avData.putIfAbsent("productPrice", 0.0);
        });

        SqlParameterSource[] batchParams = SqlParameterSourceUtils.createBatch(batch);
        int[] updateCounts = namedParameterJdbcTemplate.batchUpdate(sql, batchParams);

        return Arrays.stream(updateCounts).sum();
    }

    /**
     * Сохраняет клиентов
     */
    private int saveAvHandbook(List<Map<String, Object>> batch, ImportSession session) {
        String sql = "INSERT INTO av_handbook (handbook_retail_network_code, handbook_retail_network, handbook_physical_address, handbook_price_zone_code, handbook_web_site, " +
                "handbook_region_code, handbook_region_name, created_at, updated_at) " +
                "VALUES (:handbookRetailNetworkCode, :handbookRetailNetwork, :handbookPhysicalAddress, :handbookPriceZoneCode, :handbookWebSite, " +
                ":handbookRegionCode, :handbookRegionName, :createdAt, :updatedAt)";

        // Добавляем системные поля
        LocalDateTime now = LocalDateTime.now();
        batch.forEach(av_handbook -> {
            // Заполняем отсутствующие ключи значением null, чтобы
            // NamedParameterJdbcTemplate не выбрасывал исключение
            AV_HANDBOOK_PARAMS.forEach(param -> av_handbook.putIfAbsent(param, null));

            av_handbook.putIfAbsent("createdAt", now);
            av_handbook.putIfAbsent("updatedAt", now);
        });

        SqlParameterSource[] batchParams = SqlParameterSourceUtils.createBatch(batch);
        int[] updateCounts = namedParameterJdbcTemplate.batchUpdate(sql, batchParams);

        return Arrays.stream(updateCounts).sum();
    }

    /**
     * Откатывает импорт (удаляет импортированные записи)
     */
    @Transactional
    public void rollbackImport(ImportSession session) {
        log.warn("Откат импорта для сессии: {}", session.getId());

        // Это упрощенная версия
        // В реальности нужно отслеживать ID импортированных записей
        // и удалять только их

        // Можно добавить поле import_session_id в целевые таблицы
        // и удалять по нему:
        // DELETE FROM products WHERE import_session_id = ?

        log.info("Откат импорта завершен");
    }
}