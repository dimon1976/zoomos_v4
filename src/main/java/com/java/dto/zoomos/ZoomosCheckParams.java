package com.java.dto.zoomos;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

/**
 * Параметры запуска проверки выкачки (Zoomos Check).
 * Заменяет 12-параметровую сигнатуру метода ZoomosCheckService.runCheck().
 */
@Value
@Builder
public class ZoomosCheckParams {
    Long shopId;
    Long scheduleId;   // null если запуск не привязан к конкретному расписанию
    LocalDate dateFrom;
    LocalDate dateTo;
    String timeFrom;
    String timeTo;
    int dropThreshold;
    int errorGrowthThreshold;
    int baselineDays;
    int minAbsoluteErrors;
    int trendDropThreshold;
    int trendErrorThreshold;
    String operationId;
}
