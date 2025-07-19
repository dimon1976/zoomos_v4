package com.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "av_handbook")
public class AvHandbook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;


    private String handbookRetailNetworkCode;
    private String handbookRetailNetwork;
    private String handbookPhysicalAddress;
    private String handbookPriceZoneCode;
    private String handbookWebSite;
    private String handbookRegionCode;
    private String handbookRegionName;

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
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Код Розничной Сети", "handbookRetailNetworkCode");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Розничная Сеть", "handbookRetailNetwork");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Физический Адрес", "handbookPhysicalAddress");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Код Ценовой Зоны", "handbookPriceZoneCode");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Web-Сайт", "handbookWebSite");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Код Региона", "handbookRegionCode");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Название Региона", "handbookRegionName");
    }
}
