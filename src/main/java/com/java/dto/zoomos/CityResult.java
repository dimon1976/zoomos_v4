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
    boolean isStalled
) {}
