package com.java.service;

import com.java.model.enums.EntityType;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EntityFieldService {

    private static final Map<EntityType, List<String>> ENTITY_FIELDS = new EnumMap<>(EntityType.class);

    static {
        ENTITY_FIELDS.put(EntityType.AV_DATA, List.of(
                "dataSource", "operationId", "clientId", "productId", "productName", "productBrand", "productBar", "productDescription",
                "productUrl", "productCategory1", "productCategory2", "productCategory3", "productPrice", "productAnalog", "productAdditional1",
                "productAdditional2", "productAdditional3", "productAdditional4", "productAdditional5", "productAdditional6", "productAdditional7",
                "productAdditional8", "productAdditional9", "productAdditional10", "region", "regionAddress", "regionCountry", "competitorName",
                "competitorPrice", "competitorPromotionalPrice", "competitorTime", "competitorDate", "competitorLocalDateTime", "competitorStockStatus",
                "competitorAdditionalPrice", "competitorCommentary", "competitorProductName", "competitorAdditional", "competitorAdditional2",
                "competitorAdditional3", "competitorAdditional4", "competitorAdditional5", "competitorAdditional6", "competitorAdditional7",
                "competitorAdditional8", "competitorAdditional9", "competitorAdditional10", "competitorUrl", "competitorWebCacheUrl",
                "competitorBrand", "competitorCategory1", "competitorCategory2", "competitorCategory3", "competitorCategory4",
                "competitorProductId", "competitorBar", "competitorOldPrice", "zmsId"
        ));

        ENTITY_FIELDS.put(EntityType.AV_HANDBOOK, List.of(
                "handbookRetailNetworkCode", "handbookRetailNetwork", "handbookPhysicalAddress", "handbookPriceZoneCode", "handbookWebSite",
                "handbookRegionCode", "handbookRegionName"
        ));

        ENTITY_FIELDS.put(EntityType.BH_BARCODE_NAME, List.of(
                "barcode", "name", "brand", "manufacturerCode"
        ));

        ENTITY_FIELDS.put(EntityType.BH_NAME_URL, List.of(
                "name", "brand", "url", "siteName"
        ));
    }

    public List<String> getFields(EntityType entityType) {
        if (entityType == null) {
            return Collections.emptyList();
        }
        return ENTITY_FIELDS.getOrDefault(entityType, Collections.emptyList());
    }
}