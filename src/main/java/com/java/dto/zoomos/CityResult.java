package com.java.dto.zoomos;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.ZonedDateTime;
import java.util.List;

public record CityResult(
    String cityId,
    String cityName,
    ZoomosResultLevel status,
    Integer inStock,
    List<SiteIssue> issues,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    ZonedDateTime estimatedFinish,
    Boolean estimatedFinishReliable,
    boolean isStalled,
    Double baselineInStock,
    Integer inStockDelta,
    Integer inStockDeltaPercent,
    Long cityIdsId,
    Boolean hasConfigIssue,
    String configIssueType,
    String configIssueNote,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    java.time.LocalDate lastKnownDate,
    Integer lastKnownInStock,
    Integer lastKnownCompletionPercent,
    Boolean lastKnownIsStalled,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yy.MM.dd HH:mm")
    ZonedDateTime lastKnownUpdatedTime
) {}
