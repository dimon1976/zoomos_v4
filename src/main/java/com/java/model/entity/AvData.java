package com.java.model.entity;

import com.java.model.enums.DataSourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Сущность, представляющая товар в системе.
 */
@Setter
@Getter
@Entity
@Table(name = "av_data")
public class AvData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source")
    private DataSourceType dataSource;

    @Column(name = "operation_id")
    private Long operationId;

    private Long clientId;

    // Основные поля товара
    private String productId;

    @Column(length = 400)
    private String productName;

    private String productBrand;
    private String productBar;

    @Column(columnDefinition = "TEXT")
    private String productDescription;

    @Column(columnDefinition = "TEXT")
    private String productUrl;

    private String productCategory1;
    private String productCategory2;
    private String productCategory3;
    private Double productPrice;
    private String productAnalog;

    // Дополнительные поля
    @Column(length = 1000)
    private String productAdditional1;
    @Column(length = 1000)
    private String productAdditional2;
    @Column(length = 1000)
    private String productAdditional3;
    @Column(length = 1000)
    private String productAdditional4;
    @Column(length = 1000)
    private String productAdditional5;
    @Column(length = 1000)
    private String productAdditional6;
    @Column(length = 1000)
    private String productAdditional7;
    @Column(length = 1000)
    private String productAdditional8;
    @Column(length = 1000)
    private String productAdditional9;
    @Column(length = 1000)
    private String productAdditional10;

    private String region;

    @Column(length = 400)
    private String regionAddress;

    //Competitor

    @Column(length = 400)
    private String competitorName;

    private Double competitorPrice;
    private Double competitorPromotionalPrice;
    private String competitorTime;
    private String competitorDate;
    private LocalDateTime competitorLocalDateTime;
    private String competitorStockStatus;
    private Double competitorAdditionalPrice;

    @Column(columnDefinition = "TEXT")
    private String competitorCommentary;

    @Column(columnDefinition = "TEXT")
    private String competitorProductName;

    @Column(length = 1000)
    private String competitorAdditional;
    @Column(length = 1000)
    private String competitorAdditional2;
    @Column(length = 1000)
    private String competitorAdditional3;
    @Column(length = 1000)
    private String competitorAdditional4;
    @Column(length = 1000)
    private String competitorAdditional5;
    @Column(length = 1000)
    private String competitorAdditional6;
    @Column(length = 1000)
    private String competitorAdditional7;
    @Column(length = 1000)
    private String competitorAdditional8;
    @Column(length = 1000)
    private String competitorAdditional9;
    @Column(length = 1000)
    private String competitorAdditional10;

    @Column(columnDefinition = "TEXT")
    private String competitorUrl;

    @Column(columnDefinition = "TEXT")
    private String competitorWebCacheUrl;

    // Новые поля конкурентов (V19)
    private String competitorBrand;
    private String competitorCategory1;
    private String competitorCategory2;
    private String competitorCategory3;
    private String competitorCategory4;
    private String competitorProductId;
    private String competitorBar;
    private Double competitorOldPrice;

    // Новые поля региона (V19)
    private String regionCountry;

    // Системные поля (V19)
    private String zmsId;


    // =====================================================================================
    // ВНИМАНИЕ! ЭТО НЕ ЗАГОЛОВКИ CSV ФАЙЛОВ!
    //
    // Это человекочитаемые названия для отображения в интерфейсе при создании маппингов.
    // Пользователь видит эти названия и сопоставляет их с реальными заголовками CSV.
    //
    // Реальные заголовки CSV приходят через FieldMappingDetail.sourceField!
    // =====================================================================================
    private static final Map<String, String> UI_DISPLAY_NAMES_TO_ENTITY_FIELDS = new HashMap<>();

    static {
        // "Как показать пользователю в UI" -> "имя поля в Java сущности"
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("ID товара", "productId");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Модель", "productName");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Бренд", "productBrand");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Штрихкод", "productBar");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Описание", "productDescription");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Ссылка", "productUrl");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Категория товара 1", "productCategory1");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Категория товара 2", "productCategory2");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Категория товара 3", "productCategory3");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Цена", "productPrice");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Аналог", "productAnalog");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 1", "productAdditional1");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 2", "productAdditional2");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 3", "productAdditional3");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 4", "productAdditional4");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 5", "productAdditional5");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 6", "productAdditional6");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 7", "productAdditional7");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 8", "productAdditional8");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 9", "productAdditional9");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 10", "productAdditional10");

        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Город", "region");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Адрес", "regionAddress");

        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Сайт", "competitorName");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Цена конкурента", "competitorPrice");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Акционная цена", "competitorPromotionalPrice");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Время", "competitorTime");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дата", "competitorDate");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дата:Время", "competitorLocalDateTime");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Статус", "competitorStockStatus");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительная цена конкурента", "competitorAdditionalPrice");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Комментарий конкурента", "competitorCommentary");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Наименование товара конкурента", "competitorProductName");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле конкурента", "competitorAdditional");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 2 конкурента", "competitorAdditional2");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 3 конкурента", "competitorAdditional3");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 4 конкурента", "competitorAdditional4");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 5 конкурента", "competitorAdditional5");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 6 конкурента", "competitorAdditional6");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 7 конкурента", "competitorAdditional7");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 8 конкурента", "competitorAdditional8");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 9 конкурента", "competitorAdditional9");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 10 конкурента", "competitorAdditional10");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Ссылка на конкурента", "competitorUrl");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Скриншот", "competitorWebCacheUrl");

        // Новые поля конкурентов (V19)
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Бренд конкурента", "competitorBrand");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Категория 1 конкурента", "competitorCategory1");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Категория 2 конкурента", "competitorCategory2");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Категория 3 конкурента", "competitorCategory3");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Категория 4 конкурента", "competitorCategory4");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("ID товара конкурента", "competitorProductId");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Штрих-код конкурента", "competitorBar");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Старая цена конкурента", "competitorOldPrice");

        // Новые поля региона (V19)
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Страна", "regionCountry");

        // Системные поля (V19)
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("ID Zoomos", "zmsId");
    }
}
