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
                "productAdditional2", "productAdditional3", "productAdditional4", "productAdditional5", "region", "regionAddress", "competitorName",
                "competitorPrice", "competitorPromotionalPrice", "competitorTime", "competitorDate", "competitorLocalDateTime", "competitorStockStatus",
                "competitorAdditionalPrice", "competitorCommentary", "competitorProductName", "competitorAdditional", "competitorAdditional2", "competitorUrl",
                "competitorWebCacheUrl"
        ));

        ENTITY_FIELDS.put(EntityType.AV_HANDBOOK, List.of(
                "handbookRetailNetworkCode", "handbookRetailNetwork", "handbookPhysicalAddress", "handbookPriceZoneCode", "handbookWebSite",
                "handbookRegionCode", "handbookRegionName"
        ));
    }

    public List<String> getFields(EntityType entityType) {
        if (entityType == null) {
            return Collections.emptyList();
        }
        return ENTITY_FIELDS.getOrDefault(entityType, Collections.emptyList());
    }
}