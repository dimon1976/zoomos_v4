package com.java.dto.zoomos;

import com.java.model.entity.ZoomosParsingStats;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.ArrayList;

@Data
@Builder
public class ZoomosSiteResult {

    private String siteName;
    private String cityId;
    private String cityName;
    private String accountName;
    private String checkType;

    private ZoomosResultLevel status;
    /** Список проблем с форматированными сообщениями. */
    private List<SiteIssue> statusReasons;

    /** Последняя завершённая запись из zoomos_parsing_stats. Lazy-поля помечены @JsonIgnore в entity. */
    private ZoomosParsingStats latestStat;

    private Double baselineInStock;
    private Double baselineErrorRate;
    private Double baselineSpeedMinsPer1000;

    private ZonedDateTime estimatedFinish;
    private Boolean estimatedFinishReliable;
    private Boolean isStalled;
    private List<SparklinePoint> inStockHistory;
    private List<CityResult> cityResults;
}
